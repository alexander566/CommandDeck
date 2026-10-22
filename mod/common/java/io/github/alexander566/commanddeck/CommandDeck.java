package io.github.alexander566.commanddeck;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Listens on 127.0.0.1 for commands from the CommandDeck Stream Deck plugin and sends them as if typed in chat.
 * The OS picks a free port; port + a random token go into ~/.commanddeck/<pid>.json for the plugin to find.
 * Requests need that token and must not carry an Origin header, so web pages in a browser cannot reach it.
 * Kept to Java 8 so the same file builds for every Minecraft version; only {@link Send} differs per version.
 */
public final class CommandDeck {

	public static final Logger LOG = Logger.getLogger("CommandDeck");
	private static final int MAX_BODY = 8192;
	private static final Gson GSON = new Gson();
	private static final Path DIR = Paths.get(System.getProperty("user.home"), ".commanddeck");

	private static final String token = String.format("%032x", new java.math.BigInteger(1, new java.security.SecureRandom().generateSeed(16)));

	private static boolean started;

	/** Called by each loader's entry class; does nothing on a dedicated server. */
	public static synchronized void start() {
		if (started) {
			return;
		}
		started = true;
		try {
			Class.forName("net.minecraft.client.Minecraft", false, CommandDeck.class.getClassLoader());
		} catch (ClassNotFoundException e) {
			return;
		}
		try {
			HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
			server.createContext("/", CommandDeck::handle);
			server.setExecutor(Executors.newSingleThreadExecutor(r -> {
				Thread t = new Thread(r, "CommandDeck");
				t.setDaemon(true);
				return t;
			}));
			server.start();
			int port = server.getAddress().getPort();
			Path file = DIR.resolve(java.lang.management.ManagementFactory.getRuntimeMXBean().getName().split("@")[0] + ".json");
			JsonObject o = new JsonObject();
			o.addProperty("port", port);
			o.addProperty("token", token);
			o.addProperty("started", System.currentTimeMillis());
			Files.createDirectories(DIR);
			Files.write(file, GSON.toJson(o).getBytes(StandardCharsets.UTF_8));
			Runtime.getRuntime().addShutdownHook(new Thread(() -> {
				try {
					Files.deleteIfExists(file);
				} catch (IOException ignored) {
				}
			}));
			LOG.info("Listening on 127.0.0.1:" + port);
		} catch (IOException e) {
			LOG.log(Level.SEVERE, "Could not start CommandDeck", e);
		}
	}

	private static void handle(HttpExchange ex) throws IOException {
		try {
			if (ex.getRequestHeaders().containsKey("Origin")) {
				reply(ex, 403, "browser requests are not allowed");
				return;
			}
			if (!token.equals(ex.getRequestHeaders().getFirst("X-CommandDeck"))) {
				reply(ex, 401, "bad token");
				return;
			}
			String path = ex.getRequestURI().getPath();
			if (!path.equals("/command") || !ex.getRequestMethod().equals("POST")) {
				reply(ex, 404, "not found");
				return;
			}
			String command;
			try (InputStream in = ex.getRequestBody()) {
				ByteArrayOutputStream buf = new ByteArrayOutputStream();
				byte[] chunk = new byte[1024];
				for (int n; buf.size() <= MAX_BODY && (n = in.read(chunk)) > 0; ) {
					buf.write(chunk, 0, n);
				}
				byte[] body = buf.toByteArray();
				if (body.length > MAX_BODY) {
					reply(ex, 413, "too large");
					return;
				}
				command = new JsonParser().parse(new String(body, StandardCharsets.UTF_8)).getAsJsonObject().get("command").getAsString().trim();
			} catch (Exception e) {
				reply(ex, 400, "expected {\"command\": \"...\"}");
				return;
			}
			if (command.isEmpty()) {
				reply(ex, 400, "empty command");
				return;
			}
			String c = command;
			Boolean sent = onClient(() -> Send.send(c));
			if (sent == null) {
				reply(ex, 503, "game did not respond");
			} else if (!sent) {
				reply(ex, 409, "not in a world");
			} else {
				reply(ex, 200, "sent");
			}
		} finally {
			ex.close();
		}
	}

	private static <T> T onClient(java.util.function.Supplier<T> task) {
		CompletableFuture<T> f = new CompletableFuture<>();
		Minecraft.getInstance().execute(() -> {
			try {
				f.complete(task.get());
			} catch (Throwable t) {
				f.completeExceptionally(t);
			}
		});
		try {
			return f.get(3, TimeUnit.SECONDS);
		} catch (Exception e) {
			return null;
		}
	}

	private static void reply(HttpExchange ex, int code, String message) throws IOException {
		JsonObject o = new JsonObject();
		o.addProperty("message", message);
		reply(ex, code, o);
	}

	private static void reply(HttpExchange ex, int code, JsonObject body) throws IOException {
		byte[] out = GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
		ex.getResponseHeaders().set("Content-Type", "application/json");
		ex.sendResponseHeaders(code, out.length);
		try (OutputStream os = ex.getResponseBody()) {
			os.write(out);
		}
	}
}

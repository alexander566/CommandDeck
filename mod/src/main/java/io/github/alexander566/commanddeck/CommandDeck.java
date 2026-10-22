package io.github.alexander566.commanddeck;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Listens on 127.0.0.1 for commands from the CommandDeck Stream Deck plugin and sends them as if typed in chat.
 * The OS picks a free port; port + a random token go into ~/.commanddeck/<pid>.json for the plugin to find.
 * Requests need that token and must not carry an Origin header, so web pages in a browser cannot reach it.
 */
public final class CommandDeck implements ClientModInitializer {

	public static final Logger LOG = LoggerFactory.getLogger("CommandDeck");
	private static final int MAX_BODY = 8192;
	private static final Gson GSON = new Gson();
	private static final Path DIR = Path.of(System.getProperty("user.home"), ".commanddeck");

	private final String token = java.util.HexFormat.of().formatHex(new java.security.SecureRandom().generateSeed(16));

	@Override
	public void onInitializeClient() {
		try {
			HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
			server.createContext("/", this::handle);
			server.setExecutor(Executors.newSingleThreadExecutor(r -> {
				Thread t = new Thread(r, "CommandDeck");
				t.setDaemon(true);
				return t;
			}));
			server.start();
			int port = server.getAddress().getPort();
			Path file = DIR.resolve(ProcessHandle.current().pid() + ".json");
			JsonObject o = new JsonObject();
			o.addProperty("port", port);
			o.addProperty("token", token);
			o.addProperty("started", System.currentTimeMillis());
			Files.createDirectories(DIR);
			Files.writeString(file, GSON.toJson(o));
			Runtime.getRuntime().addShutdownHook(new Thread(() -> {
				try {
					Files.deleteIfExists(file);
				} catch (IOException ignored) {
				}
			}));
			LOG.info("Listening on 127.0.0.1:{}", port);
		} catch (IOException e) {
			LOG.error("Could not start CommandDeck", e);
		}
	}

	private void handle(HttpExchange ex) throws IOException {
		try (ex) {
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
				byte[] body = in.readNBytes(MAX_BODY + 1);
				if (body.length > MAX_BODY) {
					reply(ex, 413, "too large");
					return;
				}
				command = JsonParser.parseString(new String(body, StandardCharsets.UTF_8)).getAsJsonObject().get("command").getAsString().strip();
			} catch (Exception e) {
				reply(ex, 400, "expected {\"command\": \"...\"}");
				return;
			}
			if (command.isEmpty()) {
				reply(ex, 400, "empty command");
				return;
			}
			Boolean sent = onClient(() -> send(command));
			if (sent == null) {
				reply(ex, 503, "game did not respond");
			} else if (!sent) {
				reply(ex, 409, "not in a world");
			} else {
				reply(ex, 200, "sent");
			}
		}
	}

	private static boolean send(String command) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null) {
			return false;
		}
		if (command.startsWith("/")) {
			player.connection.sendCommand(command.substring(1));
		} else {
			player.connection.sendChat(command);
		}
		return true;
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

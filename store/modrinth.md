# CommandDeck

**Run Minecraft commands from your Stream Deck.** Press a key and the command goes straight into the game: no chat window, no typing macro, works on any server.

![CommandDeck](https://raw.githubusercontent.com/alexander566/CommandDeck/main/store/gallery-1.png)

## What you need
- This mod (client side, Fabric or Quilt, **no Fabric API needed**)
- The free **CommandDeck Stream Deck plugin**: [download from GitHub](https://github.com/alexander566/CommandDeck/releases/latest) (double-click `CommandDeck.streamDeckPlugin`)

Servers don't need anything.

## How to use
1. In the Stream Deck app, drag **CommandDeck › Command** onto a key.
2. Type a command, for example `/spawn`. The key title follows the command.
3. Join a world and press the key. ✓ means it was sent.

- One command per line, sent in order.
- Lines starting with `/` are commands, anything else is a chat message.
- Several game windows open? The key goes to the newest one that is in a world.

## Versions
| Minecraft | File |
|---|---|
| 26.1 and newer | `+mc26.x` |
| 1.19.3 – 1.21.11 | `+mc1.19.3-1.21` |
| 1.19 – 1.19.2 | `+mc1.19-1.19.2` |
| 1.14 – 1.18.2 | `+mc1.14-1.18` |

## Safety
The mod only listens on your own computer (`127.0.0.1`) on a port picked by the system, with a fresh secret every launch. Requests from web browsers are refused, so websites can't send commands to your game.

Commands are sent exactly like typed ones, so server rules about macros still apply.

Source code (MIT): https://github.com/alexander566/CommandDeck

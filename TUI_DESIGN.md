# DergPlayerAndroid TUI Design Principles

## 1. Principles

- **Monospaced Typography**: All text elements must use a monospace font (e.g., `FontFamily.Monospace`) to maintain the terminal aesthetic and ensure character alignment.
- **Retro Color Schemes**: Use high-contrast, terminal-like colors:
  - Background: Pure Black (`#000000`) or deep Matrix Green/Blue backgrounds.
  - Foreground: Bright Green (`#00FF00`), Amber (`#FFB000`), or Cyan (`#00FFFF`).
- **ASCII-style UI Elements**: Instead of smooth shadows and rounded corners:
  - Use ASCII characters for borders (`+`, `-`, `|`).
  - Use block characters for progress bars (`█`, `░`).
  - Use simple bracketed text for buttons (`[ PLAY ]`).
- **Grid Layout**: Elements should ideally align to a conceptual grid of characters.

## 2. UI Mappings

| Modern Component | TUI-Inspired Alternative |
| --- | --- |
| **Progress Bar** | `[██████░░░░░░░░] 40%` |
| **Button** | `< [ OK ] >` or `[X] Button` |
| **Slider** | `----o----` |
| **List Item** | `* Item Name` or `> Item Name` |
| **Border/Card** | ASCII box: `+-------+` \| `\| Content \|` \| `+-------+` |
| **Icon** | ASCII Art placeholders (e.g., `[#]` for artwork) or simple Unicode symbols. |

## 3. Conceptual Redesign

The redesign aims to transform the current modern UI into a retro terminal experience.

### Player Screen Mockup (Conceptual)

```
+------------------------------------------+
|  DERG PLAYER V1.0.0                      |
+------------------------------------------+
|  ARTWORK:                                |
|  [####################################]  |
|  [####################################]  |
|  [####################################]  |
+------------------------------------------+
|  TITLE : Song of the Lambda              |
|  ARTIST: Derg feat. Silence              |
+------------------------------------------+
|  PROGRESS:                               |
|  [███████████████░░░░░░░░░░░░░░░░░░░░░]  |
|  01:45 / 03:30                           |
+------------------------------------------+
|  CONTROLS:                               |
|  [ < PREV ]  [ || PAUSE ]  [ NEXT > ]    |
|                                          |
|  [ <3 LIKE ]                             |
+------------------------------------------+
```

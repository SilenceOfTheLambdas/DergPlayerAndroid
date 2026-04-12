# DergPlayerAndroid TUI Design Principles

## 1. Principles

- **Monospaced Typography**: All text elements use JetBrains Mono with font ligatures (via `TuiTheme`).
- **Retro Color Schemes**: Multiple high-contrast, terminal-like color schemes (Matrix, Amber, Cyberpunk, Dynamic).
- **Scanline Effects**: A subtle scanline overlay to enhance the CRT/terminal feel.
- **ASCII-style UI Elements**:
  - Box-drawing characters for borders (┌, ┐, └, ┘, █).
  - Block characters for progress bars (█, ░).
  - Bracketed text for buttons (`[ PLAY ]`).
- **Grid Layout**: Elements align to a conceptual grid of characters.

## 2. UI Mappings

| Modern Component | TUI-Inspired Alternative |
| --- | --- |
| **Progress Bar** | `[██████░░░░░░░░] 40%` |
| **Button** | `[ OK ]` or `[X] Button` |
| **List Item** | `TuiBorderBox` with `TuiText` |
| **Border/Card** | Box-drawing characters: `┌────┐` \| `│ │` \| `└────┘` |
| **Loading** | `LOADING...` text |

## 3. Official Design

DergPlayerAndroid is now a full-screen Terminal User Interface (TUI) application.

### Navigation

The application uses a terminal-style navigation flow:
- **Library**: List of playlists in `TuiBorderBox` containers.
- **Playlist Detail**: Song list with titles, artists, and durations.
- **Search**: Interactive terminal prompt for music discovery.
- **Player**: Full-screen terminal dashboard for playback control and visualizer.

### Interaction

- **Buttons**: All buttons are text-based and bracketed.
- **Text Fields**: Terminal prompts start with `>`.
- **Themes**: Switchable color schemes via the player dashboard.

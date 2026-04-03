# Styled Workspace Selection Dialog

Eclipse includes a prototype styled workspace selection dialog that replaces the classic workspace chooser with a modern sidebar-based UI.

## Enabling the Dialog

The styled dialog is opt-in and activated via a JVM system property:

```
-Declipse.styledWorkspaceDialog=true
```

Add this flag to your `eclipse.ini` (before the `-vmargs` line it is a program argument, but since this is a system property it goes after `-vmargs`):

```ini
-vmargs
-Declipse.styledWorkspaceDialog=true
```

## Features

The dialog provides four navigation pages accessible via a sidebar:

- **Launch** — shows recent workspaces as clickable links and a "Create a new workspace" shortcut
- **Workspaces** — select or type a workspace path, with a Browse button and a Launch button; includes a "don't ask again" checkbox
- **Recent** — placeholder for future cross-workspace activity history
- **Settings** — placeholder for launcher preferences; includes a link to disable the styled dialog and return to the classic prompt

## Implementation

The dialog is implemented in:

- `bundles/org.eclipse.ui.ide.application/src/org/eclipse/ui/internal/ide/application/StyledChooseWorkspaceDialog.java`

It is instantiated in `IDEApplication.showChooseWorkspaceDialog()` when the system property `eclipse.styledWorkspaceDialog` is `true`.

The dialog extends `org.eclipse.jface.dialogs.Dialog` and uses `ChooseWorkspaceData` (from `org.eclipse.ui.ide`) for reading and writing the workspace selection, keeping it compatible with the existing workspace preference storage.

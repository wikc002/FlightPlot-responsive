package me.drton.flightplot;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.HashMap;
import java.util.Map;

/** Owns every transient application window and prevents modal-loop re-entry. */
final class DialogCoordinator {
    private final Window owner;
    private final Map<String, JDialog> dialogs = new HashMap<String, JDialog>();
    private final Map<String, Boolean> claims = new HashMap<String, Boolean>();

    DialogCoordinator(Window owner) { this.owner = owner; }

    boolean claim(String key) {
        requireEdt();
        JDialog existing = dialogs.get(key);
        if (Boolean.TRUE.equals(claims.get(key)) || (existing != null && existing.isDisplayable())) {
            if (existing != null) { existing.toFront(); existing.requestFocus(); }
            return false;
        }
        claims.put(key, Boolean.TRUE);
        return true;
    }

    <T extends JDialog> T register(final String key, final T dialog) {
        requireEdt();
        if (!Boolean.TRUE.equals(claims.get(key))) throw new IllegalStateException("Dialog key was not claimed: " + key);
        dialog.setModalityType(Dialog.ModalityType.DOCUMENT_MODAL);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialogs.put(key, dialog);
        dialog.addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent event) { release(key, dialog); }
        });
        return dialog;
    }

    void release(String key, JDialog dialog) {
        requireEdt();
        if (dialogs.get(key) == dialog) dialogs.remove(key);
        claims.remove(key);
    }

    JDialog get(String key) { return dialogs.get(key); }
    boolean isActive(String key) { return Boolean.TRUE.equals(claims.get(key)); }

    void disposeAll() {
        requireEdt();
        for (JDialog dialog : dialogs.values().toArray(new JDialog[0])) dialog.dispose();
        dialogs.clear();
        claims.clear();
    }

    Window owner() { return owner; }

    private static void requireEdt() {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Dialog access must run on EDT");
    }
}

package dev.reactfuscator.gui;

import javax.swing.*;
import java.awt.datatransfer.*;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

public final class JarDropHandler extends TransferHandler {
    private final Consumer<Path> selected;
    public JarDropHandler(Consumer<Path> selected) { this.selected=selected; }
    @Override public boolean canImport(TransferSupport support) { return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor); }
    @Override public boolean importData(TransferSupport support) {
        if(!canImport(support)) return false;
        try { Object data=support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);if(!(data instanceof List<?> files) || files.size()!=1 || !(files.get(0) instanceof File file) || !file.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".jar")) return false;selected.accept(file.toPath());return true; }
        catch(Exception e) { return false; }
    }
}

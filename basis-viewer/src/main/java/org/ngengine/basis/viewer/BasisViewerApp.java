package org.ngengine.basis.viewer;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.swing.DefaultComboBoxModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JToolBar;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.TransferHandler;
import javax.swing.WindowConstants;
import javax.swing.filechooser.FileNameExtensionFilter;

public final class BasisViewerApp {

    private final JFrame frame = new JFrame("Basis Universal Viewer");
    private final JLabel imageLabel = new JLabel("Drop a PNG/JPG/KTX2/Basis file here", SwingConstants.CENTER);
    private final JTextArea infoArea = new JTextArea();
    private final JComboBox<ViewerImage> targetCombo = new JComboBox<>();
    private final JButton openButton = new JButton("Open");
    private final JLabel statusLabel = new JLabel("Ready");
    private ViewerDocument currentDocument;

    public static void main(String[] args) {
        EventQueue.invokeLater(() -> {
            BasisViewerApp app = new BasisViewerApp();
            app.show();
            if (args.length > 0) {
                app.load(Path.of(args[0]));
            }
        });
    }

    private BasisViewerApp() {
        configureFrame();
    }

    private void show() {
        frame.setLocationByPlatform(true);
        frame.setVisible(true);
    }

    private void configureFrame() {
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(920, 620));
        frame.setLayout(new BorderLayout());

        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.add(openButton);
        toolbar.addSeparator();
        toolbar.add(new JLabel("View target: "));
        toolbar.add(targetCombo);

        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusPanel.add(statusLabel);

        infoArea.setEditable(false);
        infoArea.setRows(8);

        JScrollPane imageScroll = new JScrollPane(imageLabel);
        JScrollPane infoScroll = new JScrollPane(infoArea);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, imageScroll, infoScroll);
        split.setResizeWeight(0.82);

        frame.add(toolbar, BorderLayout.NORTH);
        frame.add(split, BorderLayout.CENTER);
        frame.add(statusPanel, BorderLayout.SOUTH);

        openButton.addActionListener(event -> openFile());
        targetCombo.addActionListener(event -> showSelectedImage());
        frame.setTransferHandler(new FileDropHandler());
        imageLabel.setTransferHandler(new FileDropHandler());
        frame.pack();
    }

    private void openFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter(
                "Images, Basis Universal, KTX2",
                "png", "jpg", "jpeg", "bmp", "gif", "basis", "ktx", "ktx2"));
        if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            load(chooser.getSelectedFile().toPath());
        }
    }

    private void load(Path file) {
        setBusy(true, "Loading " + file.getFileName());
        new SwingWorker<ViewerDocument, Void>() {
            @Override
            protected ViewerDocument doInBackground() throws Exception {
                return ViewerDocumentLoader.load(file);
            }

            @Override
            protected void done() {
                try {
                    currentDocument = get();
                    applyDocument(currentDocument);
                    setBusy(false, "Loaded " + file.getFileName());
                } catch (Exception e) {
                    currentDocument = null;
                    targetCombo.setModel(new DefaultComboBoxModel<>());
                    imageLabel.setIcon(null);
                    imageLabel.setText("Unable to load file");
                    infoArea.setText(rootMessage(e));
                    setBusy(false, "Load failed");
                }
            }
        }.execute();
    }

    private void applyDocument(ViewerDocument document) {
        targetCombo.setModel(new DefaultComboBoxModel<>(document.getImages().toArray(ViewerImage[]::new)));
        targetCombo.setEnabled(document.getImages().size() > 1);
        infoArea.setText(document.describe());
        if (!document.getImages().isEmpty()) {
            targetCombo.setSelectedIndex(0);
            showSelectedImage();
        }
    }

    private void showSelectedImage() {
        ViewerImage selected = (ViewerImage) targetCombo.getSelectedItem();
        if (selected == null) {
            return;
        }
        BufferedImage image = selected.getImage();
        imageLabel.setText(null);
        imageLabel.setIcon(new ImageIcon(image));
        if (currentDocument != null) {
            infoArea.setText(currentDocument.describe() + "\n\nCurrent view:\n" + selected.describe());
        }
    }

    private void setBusy(boolean busy, String status) {
        openButton.setEnabled(!busy);
        targetCombo.setEnabled(!busy && targetCombo.getItemCount() > 1);
        statusLabel.setText(status);
    }

    private static String rootMessage(Exception exception) {
        Throwable cause = exception;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? cause.toString() : cause.getMessage();
    }

    private final class FileDropHandler extends TransferHandler {
        @Override
        public boolean canImport(TransferSupport support) {
            return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
        }

        @Override
        public boolean importData(TransferSupport support) {
            if (!canImport(support)) {
                return false;
            }
            try {
                Transferable transferable = support.getTransferable();
                @SuppressWarnings("unchecked")
                List<File> files = (List<File>) transferable.getTransferData(DataFlavor.javaFileListFlavor);
                if (files.isEmpty()) {
                    return false;
                }
                SwingUtilities.invokeLater(() -> load(files.get(0).toPath()));
                return true;
            } catch (Exception e) {
                infoArea.setText(rootMessage(new IOException("Drop failed", e)));
                return false;
            }
        }
    }
}

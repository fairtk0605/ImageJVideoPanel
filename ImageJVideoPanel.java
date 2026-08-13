package com.kijm.gui;

import ij.ImagePlus;
import ij.gui.Roi;
import ij.gui.RoiListener;
import ij.gui.StackWindow;
import ij.gui.Toolbar;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

public class ImageJVideoPanel extends JPanel {
    private StackWindow currentWin;
    private final SwingImageCanvas canvasPanel = new SwingImageCanvas();
    private final JPanel controlContainer = new JPanel(new BorderLayout());

    public ImageJVideoPanel() {
        this.setLayout(new BorderLayout());
        this.add(canvasPanel, BorderLayout.CENTER);
        this.add(controlContainer, BorderLayout.SOUTH);

        ImagePlus.addImageListener(new ij.ImageListener() {
            @Override public void imageUpdated(ImagePlus imp) { if (canvasPanel.isCurrent(imp)) canvasPanel.repaint(); }
            @Override public void imageOpened(ImagePlus imp) {}
            @Override public void imageClosed(ImagePlus imp) {}
        });

        Roi.addRoiListener(new RoiListener() {
            @Override
            public void roiModified(ImagePlus imp, int id) {
                if (canvasPanel.isCurrent(imp)) {
                    canvasPanel.repaint();
                }
            }
        });
    }

    public void displayVideo(ImagePlus imp, String fullPath) {
        closeCurrentVideo();
        if (imp == null) { canvasPanel.setTargetImage(null); return; }

        Component movieControls = null;
        if (imp.getStackSize() > 1) {
            this.currentWin = new StackWindow(imp);
            this.currentWin.setVisible(false);
            movieControls = extractMovieControls(this.currentWin);
        }

        canvasPanel.setTargetImage(imp);
        controlContainer.removeAll();
        if (movieControls != null) {
            movieControls.setPreferredSize(new Dimension(imp.getWidth(), 80));
            controlContainer.add(movieControls, BorderLayout.CENTER);
        }
        this.revalidate(); this.repaint();
        SwingUtilities.invokeLater(() -> canvasPanel.fitToPanelSize());
    }

    public void closeCurrentVideo() {
        if (this.currentWin != null) { this.currentWin.close(); this.currentWin = null; }
        canvasPanel.setTargetImage(null);
    }

    private Component extractMovieControls(StackWindow win) {
        for (Component comp : win.getComponents()) {
            if (comp instanceof Panel || comp.getClass().getName().contains("Scrollbar")) return comp;
        }
        return null;
    }

    private static class SwingImageCanvas extends JPanel {
        private ImagePlus imp;
        private BufferedImage cachedImg;
        private int cachedFrame = -1;
        private double zoom = 1.0, srcX = 0, srcY = 0;
        private boolean isFirstPaint = true;

        public SwingImageCanvas() {
            this.setBackground(Color.DARK_GRAY);

            this.addMouseWheelListener(e -> {
                if (imp == null) return;
                int rotation = e.getWheelRotation();

                if (imp.getStackSize() > 1) 
                    imp.setSlice(Math.max(1, Math.min(imp.getCurrentSlice() - rotation, imp.getStackSize())));
            });

            this.addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (imp == null || Toolbar.getToolId() != Toolbar.MAGNIFIER) return;

                    boolean isLeft = SwingUtilities.isLeftMouseButton(e);
                    boolean isRight = SwingUtilities.isRightMouseButton(e);

                    if (e.isShiftDown()) { fitToPanelSize(); return; }

                    double oldZoom = zoom;
                    if      (isLeft)  zoom *= 1.2;
                    else if (isRight) zoom /= 1.2;
                    zoom = Math.max(0.1, Math.min(zoom, 32.0));

                    if (zoom != oldZoom) {
                        srcX = (srcX + (e.getX() / oldZoom)) - (e.getX() / zoom);
                        srcY = (srcY + (e.getY() / oldZoom)) - (e.getY() / zoom);
                    }
                    repaint();
                }
            });
        }

        public void setTargetImage(ImagePlus imp) {
            this.imp = imp; this.zoom = 1.0; this.isFirstPaint = true;
            this.cachedImg = null; this.cachedFrame = -1;
            repaint();
        }

        public boolean isCurrent(ImagePlus checkImp) { return this.imp == checkImp; }

        public void fitToPanelSize() {
            if (imp == null || imp.getProcessor() == null) return;
            double imgW = imp.getProcessor().getWidth(), imgH = imp.getProcessor().getHeight();
            if (getWidth() <= 0 || getHeight() <= 0) return;

            this.zoom = Math.max(0.1, Math.min(Math.min(getWidth() / imgW, getHeight() / imgH), 32.0));
            this.srcX = (imgW - (getWidth() / this.zoom)) / 2.0;
            this.srcY = (imgH - (getHeight() / this.zoom)) / 2.0;
            this.isFirstPaint = false;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (imp == null) return;

            if (cachedImg == null || imp.getCurrentSlice() != cachedFrame) {
                if (imp.getProcessor() != null) {
                    cachedImg = imp.getProcessor().getBufferedImage();
                    cachedFrame = imp.getCurrentSlice();
                }
            }
            if (cachedImg == null) return;

            int panelW = getWidth();
            int panelH = getHeight();

            if (isFirstPaint) {
                srcX = (cachedImg.getWidth() - panelW) / 2.0;
                srcY = (cachedImg.getHeight() - panelH) / 2.0;
                isFirstPaint = false;
            }

            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            
            int srcX1 = (int)Math.round(srcX);
            int srcY1 = (int)Math.round(srcY);
            int srcX2 = (int)Math.round(srcX + (panelW / zoom));
            int srcY2 = (int)Math.round(srcY + (panelH / zoom));
            
            g2d.drawImage(cachedImg, 0, 0, panelW, panelH, srcX1, srcY1, srcX2, srcY2, null);

            Roi roi = imp.getRoi(); 
            if (roi != null) {
                g2d.setColor(Roi.getColor() != null ? Roi.getColor() : Color.YELLOW);
                
                Rectangle r = roi.getBounds();
                
                int scrX = (int) Math.round((r.x - srcX) * zoom);
                int scrY = (int) Math.round((r.y - srcY) * zoom);
                int scrW = (int) Math.round(r.width * zoom);
                int scrH = (int) Math.round(r.height * zoom);
                
                g2d.setStroke(new BasicStroke(1.5f));
                
                if (roi.getType() == Roi.OVAL) { g2d.drawOval(scrX, scrY, scrW, scrH);} 
                else { g2d.drawRect(scrX, scrY, scrW, scrH);}
            }
            
            g2d.dispose();
        }
    }
}

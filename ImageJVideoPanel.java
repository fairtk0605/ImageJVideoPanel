package com.kijm.gui;

import ij.ImagePlus;
import ij.gui.StackWindow;
import ij.gui.Toolbar;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

public class ImageJVideoPanel extends JPanel {
    private ImagePlus currentImp;
    private StackWindow currentWin;
    private final SwingImageCanvas canvasPanel = new SwingImageCanvas();
    private final JPanel controlContainer = new JPanel(new BorderLayout());

    public ImageJVideoPanel() {
        this.setLayout(new BorderLayout());
        this.add(canvasPanel, BorderLayout.CENTER);
        this.add(controlContainer, BorderLayout.SOUTH);

        ImagePlus.addImageListener(new ij.ImageListener() {
            @Override
            public void imageUpdated(ImagePlus imp) {
                if (imp == currentImp) canvasPanel.repaint(); 
            }
            @Override public void imageOpened(ImagePlus imp) {}
            @Override public void imageClosed(ImagePlus imp) {}
        });
    }

    public void displayVideo(ImagePlus imp, String fullPath) {
        closeCurrentVideo();
        this.currentImp = imp;
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
        this.revalidate(); 
        this.repaint();
        SwingUtilities.invokeLater(() -> canvasPanel.fitToPanelSize());
    }

    public void closeCurrentVideo() {
        if (this.currentWin != null) { this.currentWin.close(); this.currentWin = null; }
        this.currentImp = null;
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

            this.addMouseWheelListener(new MouseWheelListener() {
                @Override
                public void mouseWheelMoved(MouseWheelEvent e) {
                    if (imp == null) return;
                    int rotation = e.getWheelRotation(); 
                    if (imp.getStackSize() > 1) {
                        int nextFrame = imp.getCurrentSlice() - rotation;                             
                        if (nextFrame < 1) nextFrame = 1;
                        if (nextFrame > imp.getStackSize()) nextFrame = imp.getStackSize();                                                    
                        imp.setSlice(nextFrame);
                    }
                }
            });

            this.addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (imp == null || Toolbar.getToolId() != Toolbar.MAGNIFIER) return;

                    boolean isLeft = SwingUtilities.isLeftMouseButton(e);
                    boolean isRight = SwingUtilities.isRightMouseButton(e);

                    if (e.isShiftDown()) {
                        fitToPanelSize();
                        return;
                    }

                    double oldZoom = zoom;
                    if (isLeft)  zoom *= 1.2; 
                    else if (isRight)  zoom /= 1.2;                     
                    zoom = Math.max(0.1, Math.min(zoom, 32.0));

                    if (zoom != oldZoom) {
                        double absX = srcX + (e.getX() / oldZoom);
                        double absY = srcY + (e.getY() / oldZoom);
                        srcX = absX - (e.getX() / zoom);
                        srcY = absY - (e.getY() / zoom);
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

        private void fitToPanelSize() {
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

            if (isFirstPaint) {
                srcX = (cachedImg.getWidth() - getWidth()) / 2.0;
                srcY = (cachedImg.getHeight() - getHeight()) / 2.0;
                isFirstPaint = false;
            }

            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            
            g2d.drawImage(cachedImg, 0, 0, getWidth(), getHeight(), 
                          (int)Math.round(srcX), (int)Math.round(srcY), 
                          (int)Math.round(srcX + (getWidth() / zoom)), (int)Math.round(srcY + (getHeight() / zoom)), null);
            g2d.dispose();
        }
    }
}

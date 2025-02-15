/*
 * Fireplace
 *
 * Copyright (c) 2021, Today - Brice Dutheil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.github.bric3.fireplace.flamegraph;

import io.github.bric3.fireplace.core.ui.JScrollPaneWithButton;
import io.github.bric3.fireplace.core.ui.MouseInputListenerWorkaroundForToolTipEnabledComponent;

import javax.swing.*;
import javax.swing.event.MouseInputListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Area;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

import static java.lang.Boolean.TRUE;

/**
 * Class that allows to display a flame graph.
 *
 * <p>In general the Flamegraph's raw data is an actual tree. However walking this tree require
 * substantial effort to process during painting. For this reason the actual tree must be
 * pre-processed as a list of {@link FrameBox}.
 *
 * <p>It can be used is as follows:
 *
 * <pre><code>
 * FlameGraph&lt;MyNode&gt; flameGraph = new FlameGraph&lt;&gt;();
 * flameGraph.showMinimap(false);
 * flameGraph.setData(
 *     (FrameBox&lt;MyNode&gt;) listOfFrameBox(),   // list of frames
 *     List.of(n -> n.stringRepresentation()),      // string representation candidates
 *     rootNode -> rootNode.stringRepresentation(), // root node string representation
 *     frameToColorFunction,                        // color function
 *     frameToToolTipTextFunction                   // text tooltip function
 * );
 *
 * panel.add(flameGraph.component);
 * </code></pre>
 *
 * <p>The created and <em>final</em> {@code component} is a composite that is based on a {@link
 * JScrollPane}.
 *
 * @param <T> The type of the node data.
 * @see FlameGraphPainter
 */
public class FlameGraph<T> {
  /** Simple property that allows to display some painting data statistics */
  public static String SHOW_STATS = "flamegraph.show_stats";

  private final FlameGraphCanvas<T> canvas;

  /** The final composite component that can display a flame graph. */
  public final JComponent component;

  /**
   * Creates an empty flame graph. In order to use in Swing just access the {@link #component}
   * field.
   */
  public FlameGraph() {
    canvas = new FlameGraphCanvas<>();
    ToolTipManager.sharedInstance().registerComponent(canvas);

    component =
        JScrollPaneWithButton.create(
            () -> {
              var scrollPane = new JScrollPane(canvas);
              // Code to tweak the actions
              // https://stackoverflow.com/a/71009104/48136
              // see javax.swing.plaf.basic.BasicScrollPaneUI.Actions
              //                    var actionMap = scrollPane.getActionMap();
              //                    var inputMap =
              // scrollPane.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
              // var inputMap =
              // scrollPane.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

              scrollPane.getVerticalScrollBar().setUnitIncrement(16);
              scrollPane.getHorizontalScrollBar().setUnitIncrement(16);
              new FlameGraphMouseInputListener<>(canvas).install(scrollPane);
              new MouseInputListenerWorkaroundForToolTipEnabledComponent(scrollPane)
                  .install(canvas);
              canvas.linkListenerTo(scrollPane);

              return scrollPane;
            });
  }

  /**
   * Replaces the frame to color function.
   *
   * @param frameColorFunction A function that takes a frame and returns a color.
   */
  public void setColorFunction(Function<T, Color> frameColorFunction) {
    this.canvas
        .getFlameGraphPainter()
        .ifPresent(fgp -> fgp.frameColorFunction = frameColorFunction);
  }

  /**
   * Toggle the display of a gap between frames.
   *
   * @param frameGapEnabled {@code true} to show a gap between frames, {@code false} otherwise.
   */
  public void setFrameGapEnabled(boolean frameGapEnabled) {
    canvas.getFlameGraphPainter().ifPresent(fgp -> fgp.frameGapEnabled = frameGapEnabled);
  }

  /**
   * Replaces the default color shade for the minimap. Alpha color are supported.
   *
   * @param minimapShadeColorSupplier Color supplier.
   */
  public void setMinimapShadeColorSupplier(Supplier<Color> minimapShadeColorSupplier) {
    canvas.setMinimapShadeColorSupplier(minimapShadeColorSupplier);
  }

  /** @param showMinimap {@code true} to show the minimap, {@code false} otherwise. */
  public void showMinimap(boolean showMinimap) {
    canvas.showMinimap(showMinimap);
  }

  /**
   * Replaces the default tooltip component.
   *
   * @param tooltipComponentSupplier The tooltip component supplier.
   */
  public void setTooltipComponentSupplier(Supplier<JToolTip> tooltipComponentSupplier) {
    canvas.setTooltipComponentSupplier(tooltipComponentSupplier);
  }

  /**
   * Actually set the {@link FlameGraph} with typed data and configure how to use it.
   *
   * <p>It takes a list of {@link FrameBox} objects that wraps the actual data, which is referred to
   * as <em>node</em>.
   *
   * <p>In particular this function defines the behavior to access the typed data:
   *
   * <ul>
   *   <li>Possible string candidates to display in frames, those are selected based on the
   *       available space
   *   <li>The root node text to display, if something specific is relevant, like the type of
   *       events, their number, etc.
   *   <li>The frame background color, this function can be replaced by {@link
   *       #setColorFunction(Function)}, note that the foreground color is chosen automatically
   *   <li>The tooltip text from the current node
   * </ul>
   *
   * @param frames The {@code FrameBox} list to display.
   * @param frameToStringCandidates candidates function to display in frames.
   * @param rootFrameToString the root node description.
   * @param frameColorFunction the frame to background color function.
   * @param tooltipTextFunction the frame tooltip text function.
   */
  public void setData(
      List<FrameBox<T>> frames,
      List<Function<T, String>> frameToStringCandidates,
      Function<T, String> rootFrameToString,
      Function<T, Color> frameColorFunction,
      Function<FrameBox<T>, String> tooltipTextFunction) {
    var flameGraphPainter =
        new FlameGraphPainter<>(
            frames, frameToStringCandidates, rootFrameToString, frameColorFunction);
    flameGraphPainter.frameWidthVisibilityThreshold = 2;

    canvas.setFlameGraphPainter(flameGraphPainter);
    canvas.setToolTipTextFunction(tooltipTextFunction);
    canvas.invalidate();
  }

  /**
   * Adds an arbitrary key/value "client property".
   *
   * @param key the key, use {@code null} to remove.
   * @param value the value.
   * @see JComponent#putClientProperty(Object, Object)
   */
  public void putClientProperty(String key, Object value) {
    canvas.putClientProperty(key, value);
  }

  /**
   * Returns the value of the property with the specified key.
   *
   * @param key the key.
   * @return the value
   * @see JComponent#getClientProperty(Object)
   */
  public Object getClientProperty(String key) {
    return canvas.getClientProperty(key);
  }

  /** Triggers a repaint of the component. */
  public void requestRepaint() {
    canvas.repaint();
    canvas.triggerMinimapGeneration();
  }

  private static class FlameGraphMouseInputListener<T> implements MouseInputListener {
    private Point pressedPoint;
    private final FlameGraphCanvas<T> canvas;

    public FlameGraphMouseInputListener(FlameGraphCanvas<T> canvas) {
      this.canvas = canvas;
    }

    @Override
    public void mouseDragged(MouseEvent e) {
      if ((e.getSource() instanceof JScrollPane) && pressedPoint != null) {
        var scrollPane = (JScrollPane) e.getComponent();
        var viewPort = scrollPane.getViewport();
        if (viewPort == null) {
          return;
        }

        var dx = e.getX() - pressedPoint.x;
        var dy = e.getY() - pressedPoint.y;
        var viewPortViewPosition = viewPort.getViewPosition();
        viewPort.setViewPosition(
            new Point(
                Math.max(0, viewPortViewPosition.x - dx),
                Math.max(0, viewPortViewPosition.y - dy)));
        pressedPoint = e.getPoint();
      }
    }

    @Override
    public void mousePressed(MouseEvent e) {
      if (SwingUtilities.isLeftMouseButton(e)) {
        this.pressedPoint = e.getPoint();
      }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
      pressedPoint = null;
    }

    @Override
    public void mouseClicked(MouseEvent e) {
      if (!SwingUtilities.isLeftMouseButton(e)) {
        return;
      }
      var scrollPane = (JScrollPane) e.getComponent();
      var viewPort = scrollPane.getViewport();
      var point = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), viewPort.getView());
      if (canvas.isInsideMinimap(point)) {
        // bail out
        return;
      }

      if (e.getClickCount() == 2) {
        canvas
            .getFlameGraphPainter()
            .flatMap(fgp -> fgp.zoomToFrameAt((Graphics2D) viewPort.getView().getGraphics(), point))
            .ifPresent(
                zoomPoint -> {
                  scrollPane.revalidate();
                  EventQueue.invokeLater(() -> viewPort.setViewPosition(zoomPoint));
                });

        return;
      }

      if ((e.getSource() instanceof JScrollPane)) {
        canvas
            .getFlameGraphPainter()
            .ifPresent(
                fgp -> {
                  fgp.toggleSelectedFrameAt((Graphics2D) viewPort.getView().getGraphics(), point);
                  scrollPane.repaint();
                });
      }
    }

    @Override
    public void mouseEntered(MouseEvent e) {
      // this seems to enable key navigation
      if ((e.getComponent() instanceof JScrollPane)) {
        e.getComponent().requestFocus();
      }
    }

    @Override
    public void mouseExited(MouseEvent e) {
      if ((e.getSource() instanceof JScrollPane)) {
        var scrollPane = (JScrollPane) e.getComponent();
        canvas.getFlameGraphPainter().ifPresent(FlameGraphPainter::stopHover);
        scrollPane.repaint();
      }
    }

    @Override
    public void mouseMoved(MouseEvent e) {
      var scrollPane = (JScrollPane) e.getComponent();
      var viewPort = scrollPane.getViewport();
      var view = (JComponent) viewPort.getView();
      var point = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), view);

      if (canvas.isInsideMinimap(point)) {
        // bail out
        return;
      }

      canvas
          .getFlameGraphPainter()
          .ifPresent(
              fgp ->
                  fgp.hoverFrameAt(
                      (Graphics2D) view.getGraphics(),
                      point,
                      frame -> {
                        canvas.setToolTipText(frame);
                        scrollPane.repaint();
                      }));
    }

    public void install(JScrollPane sp) {
      sp.addMouseListener(this);
      sp.addMouseMotionListener(this);
    }
  }

  private static class FlameGraphCanvas<T> extends JPanel {
    private Image minimap;
    private JToolTip toolTip;
    private FlameGraphPainter<T> flameGraphPainter;
    private Function<FrameBox<T>, String> tooltipToTextFunction;
    private Dimension flameGraphDimension;
    private int minimapWidth = 200;
    private int minimapHeight = 100;
    private int minimapInset = 10;
    private int minimapRadius = 10;
    private Point minimapLocation = new Point(50, 50);
    private Supplier<Color> minimapShadeColorSupplier = null;
    private boolean showMinimap = true;
    private Supplier<JToolTip> tooltipComponentSupplier;

    public FlameGraphCanvas() {}

    public FlameGraphCanvas(FlameGraphPainter<T> flameGraphPainter) {
      this.flameGraphPainter = flameGraphPainter;
    }

    /** Override this method to listen to LaF changes. */
    @Override
    public void updateUI() {
      super.updateUI();
      if (flameGraphPainter != null) {
        flameGraphPainter.updateUI();
      }
    }

    @Override
    public Dimension getPreferredSize() {
      Dimension defaultDimension = super.getPreferredSize();
      defaultDimension = (defaultDimension == null) ? new Dimension(100, 50) : defaultDimension;

      if (flameGraphPainter == null) {
        return defaultDimension;
      }

      Insets insets = getInsets();
      var flameGraphDimension =
          flameGraphPainter.computeFlameGraphDimension(
              (Graphics2D) getGraphics(), getVisibleRect(), insets);
      defaultDimension.width =
          Math.max(defaultDimension.width, flameGraphDimension.width + insets.left + insets.right);
      defaultDimension.height =
          Math.max(
              defaultDimension.height, flameGraphDimension.height + insets.top + insets.bottom);

      // When the preferred size is discovered, also set the actual
      // canvas size, as it is needed during `viewPort.setViewPosition`
      // if it is not then the viewport will not be able to scroll to the
      // right frame when the flamegraph is zoomed (ie its dimensions
      // change)
      setSize(defaultDimension);

      // trigger minimap generation
      if (!flameGraphDimension.equals(this.flameGraphDimension)) {
        triggerMinimapGeneration();
      }
      this.flameGraphDimension = flameGraphDimension;
      return defaultDimension;
    }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      if (flameGraphPainter == null) {
        var noData = new JLabel("No data to display");
        noData.setBorder(BorderFactory.createEmptyBorder(50, 50, 50, 50));
        noData.setBounds(getBounds());
        noData.paint(g);
        return;
      }

      var visibleRect = getVisibleRect();
      flameGraphPainter.paintDetails = getClientProperty(SHOW_STATS) == TRUE;
      flameGraphPainter.paint((Graphics2D) g, visibleRect);
      paintMinimap(g, visibleRect);
    }

    private void paintMinimap(Graphics g, Rectangle visibleRect) {
      if (showMinimap && minimap != null) {
        var g2 =
            (Graphics2D)
                g.create(
                    visibleRect.x + minimapLocation.x,
                    visibleRect.y + visibleRect.height - minimapHeight - minimapLocation.y,
                    minimapWidth + minimapInset * 2,
                    minimapHeight + minimapInset * 2);

        g2.setColor(getBackground());
        g2.fillRoundRect(
            1,
            1,
            minimapWidth + 2 * minimapInset - 1,
            minimapHeight + 2 * minimapInset - 1,
            minimapRadius,
            minimapRadius);
        g2.drawImage(minimap, minimapInset, minimapInset, null);

        // the image is already rendered, so the hints are only for the shapes below
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setColor(getForeground());
        g2.setStroke(new BasicStroke(2));
        g2.drawRoundRect(
            1,
            1,
            minimapWidth + 2 * minimapInset - 2,
            minimapHeight + 2 * minimapInset - 2,
            minimapRadius,
            minimapRadius);

        {
          // Zoom zone
          double zoomZoneScaleX = (double) minimapWidth / flameGraphDimension.width;
          double zoomZoneScaleY = (double) minimapHeight / flameGraphDimension.height;

          int x = (int) (visibleRect.x * zoomZoneScaleX);
          int y = (int) (visibleRect.y * zoomZoneScaleY);
          int w = (int) (visibleRect.width * zoomZoneScaleX);
          int h = (int) (visibleRect.height * zoomZoneScaleY);

          var zoomZone =
              new Area(new Rectangle(minimapInset, minimapInset, minimapWidth, minimapHeight));
          zoomZone.subtract(new Area(new Rectangle(x + minimapInset, y + minimapInset, w, h)));

          var color =
              minimapShadeColorSupplier == null
                  ? new Color(getBackground().getRGB() & 0x90_FFFFFF, true)
                  : minimapShadeColorSupplier.get();
          g2.setColor(color);
          g2.fill(zoomZone);

          g2.setColor(getForeground());
          g2.setStroke(new BasicStroke(1));
          g2.drawRect(x + minimapInset, y + minimapInset, w, h);
        }
        g2.dispose();
      }
    }

    @Override
    public String getToolTipText(MouseEvent e) {
      if (isInsideMinimap(e.getPoint())) {
        return "";
      }

      return super.getToolTipText(e);
    }

    public boolean isInsideMinimap(Point point) {
      var visibleRect = getVisibleRect();
      var rectangle =
          new Rectangle(
              visibleRect.x + minimapLocation.y,
              visibleRect.y + visibleRect.height - minimapHeight - minimapLocation.y,
              minimapWidth + 2 * minimapInset,
              minimapHeight + 2 * minimapInset);

      return rectangle.contains(point);
    }

    public void setToolTipText(FrameBox<T> frame) {
      if (tooltipToTextFunction == null) {
        return;
      }
      setToolTipText(tooltipToTextFunction.apply(frame));
    }

    @Override
    public JToolTip createToolTip() {
      if (tooltipComponentSupplier == null) {
        return super.createToolTip();
      }
      if (toolTip == null) {
        toolTip = tooltipComponentSupplier.get();
        toolTip.setComponent(this);
      }

      return toolTip;
    }

    private void triggerMinimapGeneration() {
      if (!showMinimap || flameGraphPainter == null) {
        return;
      }

      CompletableFuture.runAsync(
              () -> {
                var height = flameGraphPainter.computeFlameGraphMinimapHeight(minimapWidth);
                if (height == 0) {
                  return;
                }

                GraphicsEnvironment e = GraphicsEnvironment.getLocalGraphicsEnvironment();
                GraphicsConfiguration c = e.getDefaultScreenDevice().getDefaultConfiguration();
                BufferedImage minimapImage =
                    c.createCompatibleImage(minimapWidth, height, Transparency.TRANSLUCENT);
                Graphics2D minimapGraphics = minimapImage.createGraphics();
                minimapGraphics.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                minimapGraphics.setRenderingHint(
                    RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                minimapGraphics.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

                flameGraphPainter.paintMinimap(
                    minimapGraphics, new Rectangle(minimapWidth, height));
                minimapGraphics.dispose();

                SwingUtilities.invokeLater(() -> this.setMinimapImage(minimapImage));
              })
          .handle(
              (__, t) -> {
                if (t != null) {
                  t.printStackTrace(); // no thumbnail
                }
                return null;
              });
    }

    private void setMinimapImage(BufferedImage i) {
      this.minimap = i.getScaledInstance(minimapWidth, minimapHeight, Image.SCALE_SMOOTH);
      var visibleRect = getVisibleRect();

      repaint(
          visibleRect.x + minimapLocation.x,
          visibleRect.y + visibleRect.height - minimapHeight - minimapLocation.y,
          minimapWidth + minimapInset * 2,
          minimapHeight + minimapInset * 2);
    }

    public void linkListenerTo(JScrollPane scrollPane) {
      var mouseAdapter =
          new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
              if (isInsideMinimap(e.getPoint())) {
                processMinimapMouseEvent(e);
              }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
              if (isInsideMinimap(e.getPoint())) {
                processMinimapMouseEvent(e);
              }
            }

            private void processMinimapMouseEvent(MouseEvent e) {
              if (flameGraphDimension == null) {
                return;
              }

              var pt = e.getPoint();
              if (!(e.getComponent() instanceof FlameGraph.FlameGraphCanvas)) {
                return;
              }

              var visibleRect = ((FlameGraphCanvas<?>) e.getComponent()).getVisibleRect();

              double zoomZoneScaleX = (double) minimapWidth / flameGraphDimension.width;
              double zoomZoneScaleY = (double) minimapHeight / flameGraphDimension.height;

              var h = (pt.x - (visibleRect.x + minimapLocation.x)) / zoomZoneScaleX;
              var horizontalBarModel = scrollPane.getHorizontalScrollBar().getModel();
              horizontalBarModel.setValue((int) h - horizontalBarModel.getExtent());

              var v =
                  (pt.y - (visibleRect.y + visibleRect.height - minimapHeight - minimapLocation.y))
                      / zoomZoneScaleY;
              var verticalBarModel = scrollPane.getVerticalScrollBar().getModel();
              verticalBarModel.setValue((int) v - verticalBarModel.getExtent());
            }

            @Override
            public void mouseMoved(MouseEvent e) {
              setCursor(
                  isInsideMinimap(e.getPoint())
                      ? Cursor.getPredefinedCursor(
                          System.getProperty("os.name").startsWith("Mac")
                              ? Cursor.HAND_CURSOR
                              : Cursor.MOVE_CURSOR)
                      : Cursor.getDefaultCursor());
            }
          };
      this.addMouseListener(mouseAdapter);
      this.addMouseMotionListener(mouseAdapter);
    }

    public void setFlameGraphPainter(FlameGraphPainter<T> flameGraphPainter) {
      this.flameGraphPainter = flameGraphPainter;
    }

    public Optional<FlameGraphPainter<T>> getFlameGraphPainter() {
      return Optional.ofNullable(flameGraphPainter);
    }

    public void setToolTipTextFunction(Function<FrameBox<T>, String> tooltipTextFunction) {
      this.tooltipToTextFunction = tooltipTextFunction;
    }

    public void setTooltipComponentSupplier(Supplier<JToolTip> tooltipComponentSupplier) {
      this.tooltipComponentSupplier = tooltipComponentSupplier;
    }

    public void setMinimapShadeColorSupplier(Supplier<Color> minimapShadeColorSupplier) {
      this.minimapShadeColorSupplier = minimapShadeColorSupplier;
    }

    public void showMinimap(boolean showMinimap) {
      this.showMinimap = showMinimap;
      repaint();
    }
  }
}

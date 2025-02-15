/*
 * Fireplace
 *
 * Copyright (c) 2021, Today - Brice Dutheil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.github.bric3.fireplace;

import io.github.bric3.fireplace.core.ui.Colors;
import io.github.bric3.fireplace.core.ui.Colors.Palette;
import io.github.bric3.fireplace.flamegraph.ColorMapper;
import io.github.bric3.fireplace.flamegraph.FlameGraph;
import org.openjdk.jmc.common.util.FormatToolkit;
import org.openjdk.jmc.flightrecorder.stacktrace.tree.Node;
import org.openjdk.jmc.flightrecorder.stacktrace.tree.StacktraceTreeModel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.function.Consumer;

import static java.util.stream.Collectors.joining;

public class FlameGraphTab extends JPanel {
  private static final Palette DEFAULT_COLOR_PALETTE = Palette.DATADOG;
  private static final JfrFrameColorMode DEFAULT_FRAME_COLOR_MODE = JfrFrameColorMode.BY_PACKAGE;
  private static final boolean DEFAULT_PAINT_FRAME_BORDER = true;
  private static final long serialVersionUID = 1L;
  private FlameGraph<Node> jfrFlameGraph;
  private Consumer<FlameGraph<Node>> dataApplier;

  public FlameGraphTab() {
    super(new BorderLayout());

    jfrFlameGraph = new FlameGraph<>();
    jfrFlameGraph.putClientProperty(FlameGraph.SHOW_STATS, true);
    // jfrFlameGraph.setTooltipComponentSupplier(BalloonToolTip::new);
    jfrFlameGraph.setMinimapShadeColorSupplier(
        () -> Colors.isDarkMode() ? Colors.translucent_black_40 : Colors.translucent_white_80);
    var wrapper = new JPanel(new BorderLayout());
    wrapper.add(jfrFlameGraph.component);

    var timer =
        new Timer(
            2_000,
            e -> {
              jfrFlameGraph = new FlameGraph<>();
              jfrFlameGraph.putClientProperty(FlameGraph.SHOW_STATS, true);
              jfrFlameGraph.setMinimapShadeColorSupplier(
                  () ->
                      Colors.isDarkMode()
                          ? Colors.translucent_black_40
                          : Colors.translucent_white_80);
              if (dataApplier != null) {
                dataApplier.accept(jfrFlameGraph);
              }

              wrapper.removeAll();
              wrapper.add(jfrFlameGraph.component);
              wrapper.repaint(1_000);
              wrapper.revalidate();
            });
    timer.setInitialDelay(0);
    timer.setRepeats(true);

    var refreshToggle = new JToggleButton("Refresh");
    refreshToggle.addActionListener(
        e -> {
          if (timer.isRunning()) {
            timer.stop();
          } else {
            timer.start();
          }
        });

    var colorPaletteJComboBox = new JComboBox<>(Palette.values());
    colorPaletteJComboBox.setSelectedItem(DEFAULT_COLOR_PALETTE);
    var colorModeJComboBox = new JComboBox<>(JfrFrameColorMode.values());
    colorModeJComboBox.setSelectedItem(DEFAULT_FRAME_COLOR_MODE);

    ActionListener actionListener =
        e -> {
          jfrFlameGraph.setColorFunction(
              ((JfrFrameColorMode) colorModeJComboBox.getSelectedItem())
                  .colorMapperUsing(
                      ColorMapper.ofObjectHashUsing(
                          ((Palette) colorPaletteJComboBox.getSelectedItem()).colors())));
          jfrFlameGraph.requestRepaint();
        };
    colorPaletteJComboBox.addActionListener(actionListener);
    colorModeJComboBox.addActionListener(actionListener);

    var borderToggle = new JCheckBox("Border");
    borderToggle.addActionListener(
        e -> {
          jfrFlameGraph.setFrameGapEnabled(borderToggle.isSelected());
          jfrFlameGraph.requestRepaint();
        });
    borderToggle.setSelected(DEFAULT_PAINT_FRAME_BORDER);

    var controlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
    controlPanel.add(colorPaletteJComboBox);
    controlPanel.add(colorModeJComboBox);
    controlPanel.add(borderToggle);
    controlPanel.add(refreshToggle);

    add(controlPanel, BorderLayout.NORTH);
    add(wrapper, BorderLayout.CENTER);
  }

  public FlameGraphTab(StacktraceTreeModel stacktraceTreeModel) {
    this();
    setStacktraceTreeModel(stacktraceTreeModel);
  }

  public void setStacktraceTreeModel(StacktraceTreeModel stackTraceTreeModel) {
    dataApplier = dataApplier(stackTraceTreeModel);
    dataApplier.accept(jfrFlameGraph);
  }

  private Consumer<FlameGraph<Node>> dataApplier(StacktraceTreeModel stackTraceTreeModel) {
    var flatFrameList = JfrFrameNodeConverter.convert(stackTraceTreeModel);
    return (flameGraph) ->
        flameGraph.setData(
            flatFrameList,
            List.of(
                node -> node.getFrame().getHumanReadableShortString(),
                node -> node.getFrame().getMethod().getMethodName()),
            node -> {
              var events =
                  stackTraceTreeModel.getItems().stream()
                      .map(iItems -> iItems.getType().getIdentifier())
                      .collect(joining(", "));
              return "all (" + events + ")";
            },
            DEFAULT_FRAME_COLOR_MODE.colorMapperUsing(
                ColorMapper.ofObjectHashUsing(DEFAULT_COLOR_PALETTE.colors())),
            frame -> {
              if (frame.stackDepth == 0) {
                return "";
              }

              var method = frame.actualNode.getFrame().getMethod();
              var desc =
                  FormatToolkit.getHumanReadable(
                      method, false, false, true, true, true, false, false);

              return "<html>"
                  + "<b>"
                  + frame.actualNode.getFrame().getHumanReadableShortString()
                  + "</b><br>"
                  + desc
                  + "<br><hr>"
                  + frame.actualNode.getCumulativeWeight()
                  + " "
                  + frame.actualNode.getWeight()
                  + "<br>"
                  + "BCI: "
                  + frame.actualNode.getFrame().getBCI()
                  + " Line number: "
                  + frame.actualNode.getFrame().getFrameLineNumber()
                  + "<br>"
                  + "</html>";
            });
  }
}

/*
 * Copyright 2007-2010 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package com.sun.tools.visualvm.modules.tracer.impl.timeline;

import com.sun.tools.visualvm.modules.tracer.impl.timeline.TimelineChart.Row;
import org.netbeans.lib.profiler.charts.ChartOverlay;
import org.netbeans.lib.profiler.charts.swing.Utils;
import org.netbeans.lib.profiler.charts.ChartConfigurationListener;
import org.netbeans.lib.profiler.charts.ChartContext;
import org.netbeans.lib.profiler.charts.ChartSelectionListener;
import org.netbeans.lib.profiler.charts.ItemPainter;
import org.netbeans.lib.profiler.charts.ItemSelection;
import org.netbeans.lib.profiler.charts.PaintersModel;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.Timer;
import org.netbeans.lib.profiler.charts.ChartSelectionModel;
import org.netbeans.lib.profiler.charts.swing.LongRect;
import org.netbeans.lib.profiler.charts.xy.XYItemSelection;
import org.netbeans.lib.profiler.charts.xy.synchronous.SynchronousXYItem;

/**
 *
 * @author Jiri Sedlacek
 */
final class TimelineTooltipOverlay extends ChartOverlay implements ActionListener {

    static final int TOOLTIP_OFFSET = 15;
    private static final int TOOLTIP_MARGIN = 10;
    private static final int TOOLTIP_RESPONSE = 50;
    private static final int ANIMATION_STEPS = 5;

    private TimelineTooltipPainter.Model[] rowModels;

    private List<TimelineChart.Row> selectedRows = new ArrayList(1);
    private Set<Integer> selectedTimestamps = new HashSet();

    private Timer timer;
    private int currentStep;
    private Point[] targetPositions;


    TimelineTooltipOverlay(final TimelineSupport support) {
        final TimelineChart chart = support.getChart();

        if (chart.getSelectionModel() == null)
            throw new NullPointerException("No ChartSelectionModel set for " + chart); // NOI18N

        if (!Utils.forceSpeed()) {
            timer = new Timer(TOOLTIP_RESPONSE / ANIMATION_STEPS, this);
            timer.setInitialDelay(0);
        }

        setLayout(null);

        chart.getSelectionModel().addSelectionListener(new ChartSelectionListener() {

            public void selectionModeChanged(int newMode, int oldMode) {}

            public void selectionBoundsChanged(Rectangle newBounds, Rectangle oldBounds) {}

            public void highlightedItemsChanged(List<ItemSelection> currentItems,
                List<ItemSelection> addedItems, List<ItemSelection> removedItems) {
                updateTooltip(chart);
            }

            public void selectedItemsChanged(List<ItemSelection> currentItems,
                List<ItemSelection> addedItems, List<ItemSelection> removedItems) {
                selectedRows = chart.getSelectedRows();
                selectedTimestamps = support.getSelectedTimestamps();
                updateTooltip(chart);
            }

        });

        chart.addConfigurationListener(new ChartConfigurationListener.Adapter() {

            public void contentsUpdated(long offsetX, long offsetY,
                                    double scaleX, double scaleY,
                                    long lastOffsetX, long lastOffsetY,
                                    double lastScaleX, double lastScaleY,
                                    int shiftX, int shiftY) {
                if (lastOffsetX != offsetX || lastOffsetY != offsetY ||
                    scaleX != lastScaleX || scaleY != lastScaleY)
                updateTooltip(chart);
            }

        });

        chart.addRowListener(new TimelineChart.RowListener() {

            public void rowsAdded(List<Row> rows)   { updateTooltip(chart); }

            public void rowsRemoved(List<Row> rows) { updateTooltip(chart); }

            public void rowsResized(List<Row> rows) { updateTooltip(chart); }
            
        });
    }

    void setupModel(TimelineTooltipPainter.Model[] rowModels) {
        removeAll();
        
        this.rowModels = rowModels;

        for (TimelineTooltipPainter.Model rowModel : rowModels) {
            TimelineTooltipPainter painter = new TimelineTooltipPainter(false);
            add(painter);
            painter.setVisible(false);
        }

        targetPositions = new Point[rowModels.length];
    }

    private void setPosition(Point p, TimelineTooltipPainter tooltipPainter,
                             int index, boolean immediate) {
        if (getComponentCount() > 0) {
            if (p == null) {
                if (tooltipPainter.isVisible()) tooltipPainter.setVisible(false);
                if (timer != null) timer.stop();
            } else {
                if (immediate || !tooltipPainter.isVisible() || timer == null) {
                    tooltipPainter.setVisible(true);
                    tooltipPainter.setLocation(p);
                } else {
                    currentStep = 0;
                    targetPositions[index] = p;
                    timer.restart();
                }
            }
        }
    }

    public void actionPerformed(ActionEvent e) {
        for (int i = 0; i < rowModels.length; i++) {
            TimelineTooltipPainter tooltipPainter = (TimelineTooltipPainter)getComponent(i);
            Point targetPosition = targetPositions[i];
            Point currentPosition = tooltipPainter.getLocation();

            currentPosition.x += (targetPosition.x - currentPosition.x) /
                                 (ANIMATION_STEPS - currentStep);
            currentPosition.y += (targetPosition.y - currentPosition.y) /
                                 (ANIMATION_STEPS - currentStep);
            tooltipPainter.setLocation(currentPosition);
        }
        if (++currentStep == ANIMATION_STEPS) timer.stop();
    }


    private void checkAllocatedSelectionPainters() {
        int allocatedPainters = getComponentCount() - rowModels.length;
        int requiredPainters = selectedRows.size() * selectedTimestamps.size();
        if (allocatedPainters == requiredPainters) return;

        int diff = requiredPainters - allocatedPainters;
        if (diff > 0) {
            for (int i = 0; i < diff; i++) add(new TimelineTooltipPainter(true));
        } else {
            for (int i = 0; i > diff; i--) remove(getComponentCount() - 1);
            repaint();
        }
    }

    private final List<ItemSelection> selections = new ArrayList(100);

    @SuppressWarnings("element-type-mismatch")
    private void updateTooltip(TimelineChart chart) {
        if (rowModels == null) return;

        ChartSelectionModel selectionModel = chart.getSelectionModel();
        if (selectionModel == null) return;

        checkAllocatedSelectionPainters();
        
        int painterIndex = rowModels.length;
        for (TimelineChart.Row row : selectedRows) {
            ChartContext rowContext = row.getContext();
            int itemsCount = row.getItemsCount();
            TimelineTooltipPainter.Model model = rowModels[row.getIndex()];
            for (int mark : selectedTimestamps) {
                selections.clear();
                for (int itemIndex = 0; itemIndex < itemsCount; itemIndex++) {
                    SynchronousXYItem item = (SynchronousXYItem)row.getItem(itemIndex);
                    selections.add(new XYItemSelection.Default(item, mark,
                                   XYItemSelection.DISTANCE_UNKNOWN));
                }
                TimelineTooltipPainter tooltipPainter =
                        (TimelineTooltipPainter)getComponent(painterIndex++);
                tooltipPainter.update(model, selections);
                tooltipPainter.setSize(tooltipPainter.getPreferredSize());
                setPosition(selections, chart.getPaintersModel(), rowContext,
                            tooltipPainter, row.getIndex(), true);
            }
        }

        List<ItemSelection> highlightedItems =
                selectionModel.getHighlightedItems();

        boolean noSelection = highlightedItems.isEmpty();
        if (!noSelection) {
            XYItemSelection sel = (XYItemSelection)highlightedItems.get(0);
            noSelection = sel.getItem().getValuesCount() <= sel.getValueIndex();
        }

        int rowsCount = chart.getRowsCount();
        for (int i = 0; i < rowsCount; i++) {
            TimelineTooltipPainter tooltipPainter =
                    (TimelineTooltipPainter)getComponent(i);
            if (noSelection) {
                setPosition(null, tooltipPainter, i, false);
            } else {
                TimelineChart.Row row = chart.getRow(i);
                selections.clear();

                for (ItemSelection sel : highlightedItems)
                    if (row.containsItem(sel.getItem()))
                        selections.add(sel);
                
                tooltipPainter.update(rowModels[i], selections);
                tooltipPainter.setSize(tooltipPainter.getPreferredSize());
                setPosition(selections, chart.getPaintersModel(), row.getContext(), tooltipPainter, i, false);
            }
        }
    }

    private void setPosition(List<ItemSelection> selectedItems, PaintersModel paintersModel,
                             ChartContext chartContext, TimelineTooltipPainter tooltipPainter,
                             int index, boolean immediate) {
        LongRect bounds = null;

        for (ItemSelection selection : selectedItems) {
            ItemPainter painter = paintersModel.getPainter(selection.getItem());
            LongRect selBounds = painter.getSelectionBounds(selection, chartContext);
            if (bounds == null) bounds = selBounds; else LongRect.add(bounds, selBounds);
        }

        setPosition(normalizePosition(Utils.checkedRectangle(bounds), tooltipPainter,
                    chartContext), tooltipPainter, index, immediate);
    }

    private Point normalizePosition(Rectangle bounds, TimelineTooltipPainter tooltipPainter, ChartContext chartContext) {
        Point p = new Point();

        p.x = bounds.x + bounds.width + TOOLTIP_OFFSET;
        if (p.x > chartContext.getViewportWidth() - tooltipPainter.getWidth() - TOOLTIP_MARGIN)
            p.x = bounds.x - tooltipPainter.getWidth() - TOOLTIP_OFFSET;

        int rowY = Utils.checkedInt(chartContext.getViewportOffsetY());
        int rowHeight = chartContext.getViewportHeight();
        p.y = rowY + (rowHeight - tooltipPainter.getHeight()) / 2;

        return p;
    }


    public void paint(Graphics g) {
        if (getComponentCount() == 0) return;

        Rectangle bounds = new Rectangle(0, 0, getWidth(), getHeight());
        Rectangle clip = g.getClipBounds();
        if (clip == null) g.setClip(bounds);
        else g.setClip(clip.intersection(bounds));

        super.paint(g);
    }

}
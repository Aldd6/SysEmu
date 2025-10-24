package com.umg.sysemu.UI.Controller;

import com.umg.sysemu.UI.DTO.TimelineSlice;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;

import java.util.*;
import java.util.stream.Collectors;

public class GanttController {


    @FXML private AnchorPane root;
    @FXML private ScrollPane scroll;
    @FXML private Pane canvas;


    private double pxPerTick = 8.0;
    private double rowHeight = 22.0;
    private double rowGap    = 6.0;
    private double topMargin = 24.0;
    private double leftMargin= 80.0;
    private int    majorTick = 10;


    private double minBarPx   = 3.0;   // ancho mínimo de barra
    private double minLabelPx = 28.0;  // ancho mínimo para mostrar texto dentro


    public enum LaneMode  { LANE, PID, USER, POLICY } // Cómo agrupar filas
    public enum ColorMode { LANE, PID, USER, POLICY } // Cómo colorear

    private LaneMode  laneMode  = LaneMode.LANE;
    private ColorMode colorMode = ColorMode.PID;


    private final Map<String, Color> colorCache = new HashMap<>();
    private List<TimelineSlice> lastData = List.of();


    public void setScale(double pxPerTick) {
        this.pxPerTick = Math.max(1.0, pxPerTick);
        redraw();
    }
    public void setMinBarPx(double v)   { this.minBarPx   = Math.max(1.0, v); redraw(); }
    public void setMinLabelPx(double v) { this.minLabelPx = Math.max(0.0, v); redraw(); }

    public void setLaneMode(LaneMode mode) {
        this.laneMode = (mode == null) ? LaneMode.LANE : mode;
        redraw();
    }
    public void setColorMode(ColorMode mode) {
        this.colorMode = (mode == null) ? ColorMode.PID : mode;
        colorCache.clear();
        redraw();
    }


    public void setData(List<TimelineSlice> slices) {
        this.lastData = (slices == null) ? List.of() : List.copyOf(slices);
        draw(lastData);
    }

    private void redraw() {
        if (canvas == null) return;
        draw(lastData);
    }


    private void draw(List<TimelineSlice> raw) {
        canvas.getChildren().clear();
        if (raw == null || raw.isEmpty()) return;


        List<TimelineSlice> data = raw.stream()
                .map(s -> (s.queue() == null || s.queue().isBlank())
                        ? new TimelineSlice(s.startTick(), s.endTick(), s.pid(), s.scheduler(), "CPU")
                        : s)
                .toList();


        Map<String, String> laneLabel = new LinkedHashMap<>();
        List<RecordForPaint> records  = new ArrayList<>(data.size());

        for (var s : data) {
            String key   = laneKeyFor(s);
            String label = laneLabel.computeIfAbsent(key, k -> laneLabelFor(k, s));
            records.add(new RecordForPaint(s, key, label));
        }


        List<String> orderedKeys = new ArrayList<>(laneLabel.keySet());
        if (laneMode == LaneMode.PID) {
            orderedKeys.sort(Comparator.comparingLong(this::safeParseLong));
        }
        Map<String, Integer> laneIndex = new LinkedHashMap<>();
        for (int i = 0; i < orderedKeys.size(); i++) laneIndex.put(orderedKeys.get(i), i);

        int maxEnd = data.stream().mapToInt(TimelineSlice::endTick).max().orElse(0);
        double laneSlot = rowHeight + rowGap;


        double needW = leftMargin + (maxEnd * pxPerTick) + 80;
        double needH = topMargin + laneIndex.size() * laneSlot + 8;
        canvas.setMinSize(needW, needH);
        canvas.setPrefSize(needW, needH);


        drawGrid(laneIndex, maxEnd);


        for (var r : records) {
            var s = r.slice();
            int row = laneIndex.get(r.key());
            double y = topMargin + row * laneSlot + (rowGap / 2.0);
            double x = leftMargin + s.startTick() * pxPerTick;
            double w = Math.max(minBarPx, (s.endTick() - s.startTick()) * pxPerTick);
            double h = rowHeight;

            Color base = colorFor(colorKeyFor(s));
            Rectangle rect = new Rectangle(x, y, w, h);
            rect.setArcWidth(6); rect.setArcHeight(6);
            rect.setFill(base.deriveColor(0, 1, 1, 0.90));
            rect.setStroke(base.darker());

            if (w >= minLabelPx) {
                Label lbl = new Label(shortPid(s.pid()));
                lbl.setStyle("-fx-font-size: 11px; -fx-font-weight: 600; -fx-text-fill: white;");
                lbl.setMouseTransparent(true);
                double lx = x + Math.max(4, (w - computeTextWidth(lbl)) / 2.0);
                double ly = y + 2;
                lbl.relocate(lx, ly);
                canvas.getChildren().addAll(rect, lbl);
            } else {
                canvas.getChildren().add(rect);
            }

            Tooltip.install(rect, new Tooltip(
                    "PID: " + s.pid() + "\n" +
                            "Fila: " + r.label() + "\n" +
                            "Policy: " + s.scheduler() + "\n" +
                            "t=" + s.startTick() + " → " + s.endTick() + " (Δ " + (s.endTick() - s.startTick()) + ")"
            ));

            ensureLaneLabelOnce(r.key(), r.label(), y + 2);
        }
    }

    private void drawGrid(Map<String, Integer> laneIndex, int maxEnd) {
        double laneSlot = rowHeight + rowGap;


        for (int t = 0; t <= maxEnd; t++) {
            double x = leftMargin + t * pxPerTick;
            double len = (t % majorTick == 0) ? 10 : 5;
            Line tick = new Line(x, 2, x, 2 + len);
            tick.setStroke(Color.gray(0.6));
            canvas.getChildren().add(tick);

            if (t % majorTick == 0) {
                Label l = new Label(String.valueOf(t));
                l.setStyle("-fx-text-fill:#666; -fx-font-size:10px;");
                l.relocate(x - 6, 2 + len + 2);
                canvas.getChildren().add(l);
            }
        }


        for (int i = 0; i < laneIndex.size(); i++) {
            double y = topMargin + i * laneSlot + (rowGap / 2.0) - 1;
            Line hl = new Line(leftMargin, y, leftMargin + maxEnd * pxPerTick, y);
            hl.setStroke(Color.gray(0.92));
            canvas.getChildren().add(hl);
        }
    }

    private void ensureLaneLabelOnce(String key, String label, double y) {
        boolean exists = canvas.getChildren().stream().anyMatch(n ->
                (n instanceof Label lbl) && key.equals(lbl.getProperties().get("laneKey")));
        if (!exists) {
            Label laneLbl = new Label(label);
            laneLbl.getProperties().put("laneKey", key);
            laneLbl.setStyle("-fx-text-fill:#333; -fx-font-weight:600;");
            laneLbl.relocate(8, y);
            canvas.getChildren().add(laneLbl);
        }
    }


    private String laneKeyFor(TimelineSlice s) {
        return switch (laneMode) {
            case LANE   -> s.queue();
            case PID    -> String.valueOf(s.pid());
            case USER   -> inferUserFromSlice(s); // si luego agregas user al slice, úsalo aquí
            case POLICY -> (s.scheduler() == null || s.scheduler().isBlank()) ? "POLICY" : s.scheduler();
        };
    }

    private String laneLabelFor(String key, TimelineSlice s) {
        return switch (laneMode) {
            case PID    -> shortPid(safeParseLong(key));
            case LANE   -> (key == null || key.isBlank()) ? "CPU" : key;
            case USER   -> (key == null || key.isBlank()) ? "USER" : key;
            case POLICY -> (key == null || key.isBlank()) ? "POLICY" : key;
        };
    }

    private String colorKeyFor(TimelineSlice s) {
        return switch (colorMode) {
            case LANE   -> (s.queue() == null || s.queue().isBlank()) ? "CPU" : s.queue();
            case PID    -> String.valueOf(s.pid());
            case USER   -> inferUserFromSlice(s);
            case POLICY -> (s.scheduler() == null || s.scheduler().isBlank()) ? "POLICY" : s.scheduler();
        };
    }

    private String inferUserFromSlice(TimelineSlice s) {
        if (s.queue() != null && !s.queue().isBlank()) return s.queue();
        return String.valueOf(s.pid());
    }

    private Color colorFor(String key) {
        if (key == null) key = "null";
        final String k = key;
        return colorCache.computeIfAbsent(k, _k -> {
            int h = Math.abs(_k.hashCode());
            double hue = (h % 360);
            double sat = 0.60 + ((h >> 3) & 0x1F) / 255.0;   // 0.60–0.80
            double bri = 0.75 + ((h >> 7) & 0x1F) / 255.0;   // 0.75–0.95
            return Color.hsb(hue, Math.min(0.85, sat), Math.min(1.0, bri));
        });
    }


    private String shortPid(long pid) {
        String s = Long.toUnsignedString(pid);
        if (s.length() <= 4) return "#" + s;
        return "#" + s.substring(s.length() - 4);
    }

    private long safeParseLong(String s) {
        try { return Long.parseLong(s); } catch (Exception e) { return -1L; }
    }

    private double computeTextWidth(Label lbl) {
        Text helper = new Text(lbl.getText());
        helper.setFont(lbl.getFont());
        return helper.getBoundsInLocal().getWidth();
    }


    private record RecordForPaint(TimelineSlice slice, String key, String label) {}
}



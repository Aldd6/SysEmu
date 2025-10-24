package com.umg.sysemu.UI.Controller;

import com.umg.sysemu.UI.DTO.TimelineSlice; // tu DTO del kernel
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;

import java.util.*;

public class GanttController {

    @FXML private AnchorPane root;
    @FXML private ScrollPane scroll;
    @FXML private Pane canvas;

    // parámetros simples (ajústalos si quieres)
    private double pxPerTick = 8.0;
    private double rowHeight = 22.0;
    private double rowGap    = 6.0;
    private double topMargin = 24.0;
    private double leftMargin= 80.0;
    private int majorTick    = 10;

    public void setScale(double pxPerTick) {
        this.pxPerTick = Math.max(1.0, pxPerTick);
        // redibuja si ya hay datos
        if (!canvas.getChildren().isEmpty()) {
            // vuelve a pintar usando la última data si la guardas (puedes guardarla si quieres)
        }
    }

    /** Punto de entrada: pásale la lista que te da el kernel */
    public void setData(List<TimelineSlice> slices) {
        draw(slices);
    }

    // --- Dibujo principal ---
    private void draw(List<TimelineSlice> raw) {
        canvas.getChildren().clear();
        if (raw == null || raw.isEmpty()) return;

        // Normaliza lane nulo
        List<TimelineSlice> data = raw.stream()
                .map(s -> s.queue() == null || s.queue().isBlank()
                        ? new TimelineSlice(s.startTick(), s.endTick(), s.pid(), s.scheduler(), "CPU")
                        : s)
                .toList();

        // lanes en orden de aparición
        LinkedHashMap<String, Integer> laneIndex = new LinkedHashMap<>();
        for (TimelineSlice s : data) laneIndex.putIfAbsent(s.queue(), laneIndex.size());

        int maxEnd = data.stream().mapToInt(TimelineSlice::endTick).max().orElse(0);
        double laneSlot = rowHeight + rowGap;

        // definir tamaño del canvas (para permitir scroll horizontal)
        double needW = leftMargin + (maxEnd * pxPerTick) + 80;
        double needH = topMargin + laneIndex.size() * laneSlot + 8;
        canvas.setMinSize(needW, needH);
        canvas.setPrefSize(needW, needH);

        // rejilla
        drawGrid(laneIndex, maxEnd);

        // colores por lane
        Map<String, Color> laneColor = buildPalette(laneIndex.keySet());

        // pintado de bloques
        for (TimelineSlice s : data) {
            int row = laneIndex.get(s.queue());
            double y = topMargin + row * laneSlot + (rowGap / 2.0);
            double x = leftMargin + s.startTick() * pxPerTick;
            double w = Math.max(1, (s.endTick() - s.startTick()) * pxPerTick);
            double h = rowHeight;

            Color base = laneColor.getOrDefault(s.queue(), Color.STEELBLUE);
            Rectangle r = new Rectangle(x, y, w, h);
            r.setFill(base.deriveColor(0,1,1,0.85));
            r.setStroke(base.darker());

            Label lbl = new Label(String.valueOf(s.pid()));
            lbl.setTextFill(Color.WHITE);
            lbl.setMouseTransparent(true);
            lbl.relocate(x + Math.max(4, w/2 - 10), y + 2);

            Tooltip.install(r, new Tooltip(
                    "PID: " + s.pid() + "\n" +
                            "Lane: " + s.queue() + "\n" +
                            "Policy: " + s.scheduler() + "\n" +
                            "t=" + s.startTick() + " → " + s.endTick() + " (Δ " + (s.endTick()-s.startTick()) + ")"
            ));

            // label con el nombre del lane una sola vez por fila
            ensureLaneLabel(s.queue(), y + 2);

            canvas.getChildren().addAll(r, lbl);
        }
    }

    private void drawGrid(LinkedHashMap<String,Integer> laneIndex, int maxEnd) {
        double laneSlot = rowHeight + rowGap;

        // ticks verticales (regla superior)
        for (int t = 0; t <= maxEnd; t++) {
            double x = leftMargin + t * pxPerTick;
            double len = (t % majorTick == 0) ? 10 : 5;
            Line tick = new Line(x, 2, x, 2 + len);
            tick.setStroke(Color.gray(0.6));
            canvas.getChildren().add(tick);

            if (t % majorTick == 0) {
                Label l = new Label(String.valueOf(t));
                l.setTextFill(Color.gray(0.35));
                l.relocate(x - 6, 2 + len + 2);
                canvas.getChildren().add(l);
            }
        }

        // líneas horizontales por fila
        for (int i = 0; i < laneIndex.size(); i++) {
            double y = topMargin + i * laneSlot + (rowGap / 2.0) - 1;
            Line hl = new Line(leftMargin, y, leftMargin + maxEnd * pxPerTick, y);
            hl.setStroke(Color.gray(0.92));
            canvas.getChildren().add(hl);
        }
    }

    private void ensureLaneLabel(String lane, double y) {
        boolean exists = canvas.getChildren().stream().anyMatch(n ->
                (n instanceof Label lbl) && lane.equals(lbl.getProperties().get("laneName")));
        if (!exists) {
            Label laneLbl = new Label(lane);
            laneLbl.getProperties().put("laneName", lane);
            laneLbl.setTextFill(Color.web("#333"));
            laneLbl.relocate(8, y);
            canvas.getChildren().add(laneLbl);
        }
    }

    private Map<String, Color> buildPalette(Collection<String> lanes) {
        List<Color> palette = List.of(
                Color.web("#4F46E5"), Color.web("#22C55E"), Color.web("#F59E0B"),
                Color.web("#EC4899"), Color.web("#0EA5E9"), Color.web("#A855F7"),
                Color.web("#EF4444"), Color.web("#10B981"), Color.web("#64748B")
        );
        Map<String, Color> out = new HashMap<>();
        int i = 0;
        for (String lane : lanes) {
            out.put(lane, palette.get(i % palette.size()));
            i++;
        }
        return out;
    }
}


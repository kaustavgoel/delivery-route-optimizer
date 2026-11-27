package com.deliveryoptimizer.ui;

import com.deliveryoptimizer.graph.RoadNetwork;
import com.deliveryoptimizer.model.Location;
import com.deliveryoptimizer.model.Location.DeliveryStatus;
import com.deliveryoptimizer.model.Location.Priority;
import com.deliveryoptimizer.model.ProblemConfig;
import com.deliveryoptimizer.model.Results.ClusterResult;
import com.deliveryoptimizer.model.Route;
import com.deliveryoptimizer.model.Route.Road;
import com.deliveryoptimizer.model.Vehicle;
import com.deliveryoptimizer.model.Vehicle.Fleet;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class MapCanvas extends StackPane {

    private final Canvas canvas = new Canvas(ProblemConfig.MAP_WIDTH, ProblemConfig.MAP_HEIGHT);
    private final Label modeBanner = new Label("Click on map to place new delivery node");

    private RoadNetwork network;
    private Fleet fleet;
    private ClusterResult clusterOverlay;
    private boolean showClusterOverlay = false;
    private boolean showRoadWeights = false;
    private boolean addNodeMode = false;

    private Integer selectedLocationId = null;
    private Integer highlightedVehicleId = null;

    private double zoom = 1.0, panX = 0.0, panY = 0.0;
    private double dragStartX, dragStartY, dragOriginPanX, dragOriginPanY;
    private boolean dragged = false;

    private Consumer<Location> onLocationSelected;
    private Consumer<Vehicle> onVehicleSelected;
    private BiConsumer<Double, Double> onMapClickedForNewNode;

    public MapCanvas() {
        getStyleClass().add("card");
        setMinSize(540, 420);

        Pane wrapper = new Pane(canvas);
        canvas.widthProperty().bind(wrapper.widthProperty());
        canvas.heightProperty().bind(wrapper.heightProperty());
        canvas.widthProperty().addListener((o, a, b) -> render());
        canvas.heightProperty().addListener((o, a, b) -> render());

        Button zoomOut = new Button("-");
        zoomOut.getStyleClass().add("btn-secondary");
        zoomOut.setOnAction(e -> adjustZoom(1.0 / 1.18));

        Button zoomIn = new Button("+");
        zoomIn.getStyleClass().add("btn-secondary");
        zoomIn.setOnAction(e -> adjustZoom(1.18));

        Button fitBtn = new Button("Fit View");
        fitBtn.getStyleClass().add("btn-secondary");
        fitBtn.setOnAction(e -> { zoom = 1.0; panX = 0.0; panY = 0.0; render(); });

        HBox hud = new HBox(8, zoomOut, zoomIn, fitBtn);
        hud.setAlignment(Pos.CENTER_RIGHT);
        hud.setPickOnBounds(false);
        hud.setPadding(new Insets(10));
        StackPane.setAlignment(hud, Pos.TOP_RIGHT);

        modeBanner.getStyleClass().add("badge-warning");
        modeBanner.setVisible(false);
        StackPane.setAlignment(modeBanner, Pos.TOP_LEFT);
        StackPane.setMargin(modeBanner, new Insets(12));

        getChildren().addAll(wrapper, hud, modeBanner);
        installMouseHandlers();
    }

    private void installMouseHandlers() {
        canvas.addEventHandler(ScrollEvent.SCROLL, e -> {
            double newZoom = Math.max(0.45, Math.min(3.5, zoom * (e.getDeltaY() > 0 ? 1.12 : 0.89)));
            double ratio = newZoom / zoom;
            panX = e.getX() - (e.getX() - panX) * ratio;
            panY = e.getY() - (e.getY() - panY) * ratio;
            zoom = newZoom;
            render();
            e.consume();
        });
        canvas.setOnMousePressed(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                dragStartX = e.getX();
                dragStartY = e.getY();
                dragOriginPanX = panX;
                dragOriginPanY = panY;
                dragged = false;
            }
        });
        canvas.setOnMouseDragged(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                double dx = e.getX() - dragStartX, dy = e.getY() - dragStartY;
                if (Math.hypot(dx, dy) > 4.0) dragged = true;
                if (dragged) { panX = dragOriginPanX + dx; panY = dragOriginPanY + dy; render(); }
            }
        });
        canvas.setOnMouseReleased(e -> {
            if (e.getButton() == MouseButton.PRIMARY && !dragged) handleClick(e.getX(), e.getY());
        });
    }

    private void handleClick(double sx, double sy) {
        double[] world = screenToWorld(sx, sy);
        double wx = world[0], wy = world[1];
        if (addNodeMode && onMapClickedForNewNode != null) {
            onMapClickedForNewNode.accept(
                    Math.max(25, Math.min(ProblemConfig.MAP_WIDTH - 25, wx)),
                    Math.max(25, Math.min(ProblemConfig.MAP_HEIGHT - 25, wy)));
            return;
        }
        if (fleet != null) {
            Vehicle hitVeh = null;
            double best = 18.0 / zoom;
            for (Vehicle v : fleet.getVehicles()) {
                double d = Math.hypot(v.getCurrentX() - wx, v.getCurrentY() - wy);
                if (d < best) { best = d; hitVeh = v; }
            }
            if (hitVeh != null) {
                highlightedVehicleId = hitVeh.getVehicleId();
                selectedLocationId = null;
                if (onVehicleSelected != null) onVehicleSelected.accept(hitVeh);
                render();
                return;
            }
        }
        if (network != null) {
            Location hit = network.findNearestLocation(wx, wy, 20.0 / zoom);
            if (hit != null) {
                selectedLocationId = hit.getId();
                if (onLocationSelected != null) onLocationSelected.accept(hit);
                render();
            }
        }
    }

    private void adjustZoom(double factor) {
        double cx = canvas.getWidth() / 2.0, cy = canvas.getHeight() / 2.0;
        double newZoom = Math.max(0.45, Math.min(3.5, zoom * factor));
        double ratio = newZoom / zoom;
        panX = cx - (cx - panX) * ratio;
        panY = cy - (cy - panY) * ratio;
        zoom = newZoom;
        render();
    }

    private double fitScale() {
        return Math.min(Math.max(100, canvas.getWidth()) / ProblemConfig.MAP_WIDTH,
                Math.max(100, canvas.getHeight()) / ProblemConfig.MAP_HEIGHT);
    }

    private double[] worldToScreen(double wx, double wy) {
        double fit = fitScale();
        double ox = (canvas.getWidth() - ProblemConfig.MAP_WIDTH * fit) / 2.0;
        double oy = (canvas.getHeight() - ProblemConfig.MAP_HEIGHT * fit) / 2.0;
        return new double[]{(wx * fit + ox) * zoom + panX, (wy * fit + oy) * zoom + panY};
    }

    private double[] screenToWorld(double sx, double sy) {
        double fit = fitScale();
        double ox = (canvas.getWidth() - ProblemConfig.MAP_WIDTH * fit) / 2.0;
        double oy = (canvas.getHeight() - ProblemConfig.MAP_HEIGHT * fit) / 2.0;
        return new double[]{((sx - panX) / zoom - ox) / fit, ((sy - panY) / zoom - oy) / fit};
    }

    public void render() {
        double w = canvas.getWidth(), h = canvas.getHeight();
        if (w <= 0 || h <= 0) return;
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setFill(Color.web("#0A1122"));
        gc.fillRect(0, 0, w, h);

        gc.setStroke(Color.web("#14213D"));
        gc.setLineWidth(1.0);
        double step = Math.max(16, 48 * zoom);
        for (double x = ((panX % step) + step) % step; x < w; x += step) gc.strokeLine(x, 0, x, h);
        for (double y = ((panY % step) + step) % step; y < h; y += step) gc.strokeLine(0, y, w, y);

        if (network == null) return;
        drawRoads(gc);
        if (showClusterOverlay && clusterOverlay != null) drawClusters(gc);
        drawRoutes(gc);
        drawNodes(gc);
        drawVehicles(gc);
    }

    private void drawRoads(GraphicsContext gc) {
        gc.setStroke(Color.web("#233760"));
        gc.setLineWidth(1.6);
        gc.setFont(Font.font("System", 9.5));
        gc.setTextAlign(TextAlignment.CENTER);
        for (Road road : network.getRoads()) {
            double[] p1 = worldToScreen(road.getFrom().getX(), road.getFrom().getY());
            double[] p2 = worldToScreen(road.getTo().getX(), road.getTo().getY());
            gc.strokeLine(p1[0], p1[1], p2[0], p2[1]);
            if (showRoadWeights) {
                gc.setFill(Color.web("#6F84AC"));
                gc.fillText(String.format("%.0f", road.getDistance()), (p1[0] + p2[0]) / 2, (p1[1] + p2[1]) / 2 - 3);
            }
        }
    }

    private void drawClusters(GraphicsContext gc) {
        Map<Integer, Integer> nodeCluster = new HashMap<>();
        List<List<Location>> clusters = clusterOverlay.getClusters();
        for (int c = 0; c < clusters.size(); c++) {
            for (Location loc : clusters.get(c)) nodeCluster.put(loc.getId(), c);
        }
        for (Location loc : network.getDeliveryLocations()) {
            Integer cIdx = nodeCluster.get(loc.getId());
            if (cIdx == null) continue;
            double[] p = worldToScreen(loc.getX(), loc.getY());
            double r = 20 * Math.sqrt(zoom);
            gc.setFill(Color.web(ProblemConfig.getVehicleColor(cIdx), 0.25));
            gc.fillOval(p[0] - r, p[1] - r, r * 2, r * 2);
        }
    }

    private void drawRoutes(GraphicsContext gc) {
        if (fleet == null) return;
        for (Vehicle v : fleet.getVehicles()) {
            Route route = v.getAssignedRoute();
            if (route == null || route.isEmpty()) continue;
            List<Location> path = route.getFullRoadPath().size() >= 2 ? route.getFullRoadPath() : route.getRouteSequence();
            boolean hi = highlightedVehicleId == null || highlightedVehicleId == v.getVehicleId();
            Color color = Color.web(v.getColorHex(), hi ? 0.92 : 0.22);
            gc.setStroke(color);
            gc.setLineWidth(highlightedVehicleId != null && highlightedVehicleId == v.getVehicleId() ? 4.0 : 2.6);

            for (int i = 0; i < path.size() - 1; i++) {
                double[] p1 = worldToScreen(path.get(i).getX(), path.get(i).getY());
                double[] p2 = worldToScreen(path.get(i + 1).getX(), path.get(i + 1).getY());
                gc.strokeLine(p1[0], p1[1], p2[0], p2[1]);
                if (hi && Math.hypot(p2[0] - p1[0], p2[1] - p1[1]) >= 24) {
                    double mx = p1[0] + (p2[0] - p1[0]) * 0.6, my = p1[1] + (p2[1] - p1[1]) * 0.6;
                    double ang = Math.atan2(p2[1] - p1[1], p2[0] - p1[0]);
                    gc.setFill(color);
                    gc.fillPolygon(
                            new double[]{mx, mx - 7.5 * Math.cos(ang - Math.PI / 6), mx - 7.5 * Math.cos(ang + Math.PI / 6)},
                            new double[]{my, my - 7.5 * Math.sin(ang - Math.PI / 6), my - 7.5 * Math.sin(ang + Math.PI / 6)}, 3);
                }
            }
        }
    }

    private void drawNodes(GraphicsContext gc) {
        for (Location loc : network.getAllLocationsSorted()) {
            double[] p = worldToScreen(loc.getX(), loc.getY());
            double x = p[0], y = p[1];
            boolean sel = selectedLocationId != null && selectedLocationId == loc.getId();

            if (loc.isDepot()) {
                gc.setFill(Color.web("#318BFF", 0.22));
                gc.fillOval(x - 22, y - 22, 44, 44);
                if (sel) { gc.setStroke(Color.WHITE); gc.setLineWidth(2.4); gc.strokeOval(x - 20, y - 20, 40, 40); }
                gc.setFill(Color.web("#318BFF"));
                gc.fillPolygon(new double[]{x, x + 14, x, x - 14}, new double[]{y - 14, y, y + 14, y}, 4);
                gc.setStroke(Color.WHITE);
                gc.setLineWidth(2.0);
                gc.strokePolygon(new double[]{x, x + 14, x, x - 14}, new double[]{y - 14, y, y + 14, y}, 4);
                gc.setFill(Color.WHITE);
                gc.setFont(Font.font("System", FontWeight.BOLD, 11));
                gc.setTextAlign(TextAlignment.CENTER);
                gc.fillText("DEPOT", x, y - 20);
            } else {
                double r = 9.0;
                if (loc.getPriority() == Priority.URGENT || loc.getPriority() == Priority.HIGH) {
                    gc.setStroke(Color.web(loc.getPriority() == Priority.URGENT ? "#FF5364" : "#FFB547"));
                    gc.setLineWidth(2.0);
                    gc.strokeOval(x - r - 4, y - r - 4, (r + 4) * 2, (r + 4) * 2);
                }
                if (sel) { gc.setStroke(Color.WHITE); gc.setLineWidth(2.4); gc.strokeOval(x - r - 6, y - r - 6, (r + 6) * 2, (r + 6) * 2); }
                Color fill = loc.getDeliveryStatus() == DeliveryStatus.COMPLETED ? Color.web("#35D98A")
                        : (loc.getAssignedVehicleId() != null ? Color.web(ProblemConfig.getVehicleColor(loc.getAssignedVehicleId() - 1)) : Color.web("#4D6594"));
                gc.setFill(fill);
                gc.fillOval(x - r, y - r, r * 2, r * 2);
                gc.setStroke(Color.web("#0B1224"));
                gc.setLineWidth(1.5);
                gc.strokeOval(x - r, y - r, r * 2, r * 2);
                gc.setFill(Color.WHITE);
                gc.setFont(Font.font("System", FontWeight.BOLD, 9.5));
                gc.setTextAlign(TextAlignment.CENTER);
                gc.fillText(String.valueOf(loc.getId()), x, y + 3.5);
                gc.setFill(Color.web("#9FB4DA"));
                gc.setFont(Font.font("System", 9.5));
                gc.fillText("q:" + loc.getDemand() + (loc.getDeliveryStatus() == DeliveryStatus.COMPLETED ? " ✓" : ""), x, y + r + 12);
            }
        }
    }

    private void drawVehicles(GraphicsContext gc) {
        if (fleet == null) return;
        for (Vehicle v : fleet.getVehicles()) {
            if (v.getAssignedRoute() == null || v.getAssignedRoute().isEmpty()) continue;
            double[] p = worldToScreen(v.getCurrentX(), v.getCurrentY());
            double x = p[0], y = p[1];
            gc.setFill(Color.web(v.getColorHex(), 0.28));
            gc.fillOval(x - 16, y - 16, 32, 32);
            gc.setFill(Color.web(v.getColorHex()));
            gc.fillRoundRect(x - 10, y - 7, 20, 14, 5, 5);
            gc.setStroke(Color.WHITE);
            gc.setLineWidth(1.6);
            gc.strokeRoundRect(x - 10, y - 7, 20, 14, 5, 5);
            gc.setFill(Color.WHITE);
            gc.setFont(Font.font("System", FontWeight.BOLD, 9.5));
            gc.setTextAlign(TextAlignment.CENTER);
            gc.fillText("V" + v.getVehicleId(), x, y + 3.5);
        }
    }

    public void setNetworkAndFleet(RoadNetwork net, Fleet fl) { this.network = net; this.fleet = fl; render(); }
    public void setClusterOverlay(ClusterResult cr, boolean show) { this.clusterOverlay = cr; this.showClusterOverlay = show; render(); }
    public void setShowRoadWeights(boolean show) { this.showRoadWeights = show; render(); }
    public void setAddNodeMode(boolean mode) { this.addNodeMode = mode; modeBanner.setVisible(mode); }
    public boolean isAddNodeMode() { return addNodeMode; }
    public void setHighlightedVehicleId(Integer id) { this.highlightedVehicleId = id; render(); }
    public void setSelectedLocationId(Integer id) { this.selectedLocationId = id; render(); }
    public void setOnLocationSelected(Consumer<Location> l) { this.onLocationSelected = l; }
    public void setOnVehicleSelected(Consumer<Vehicle> l) { this.onVehicleSelected = l; }
    public void setOnMapClickedForNewNode(BiConsumer<Double, Double> l) { this.onMapClickedForNewNode = l; }
}

package by.agro.launcher.ui.components;

import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

public final class WindowGestures {

    private static final int EDGE_THICKNESS = 6;

    private WindowGestures() {
    }

    public static void makeDraggable(JFrame frame, JComponent handle) {
        if (frame == null || handle == null) {
            return;
        }
        DragAdapter adapter = new DragAdapter(frame);
        handle.addMouseListener(adapter);
        handle.addMouseMotionListener(adapter);
    }

    public static void installResize(JFrame frame) {
        if (frame == null) {
            return;
        }
        JLayeredPane layeredPane = frame.getLayeredPane();
        Direction[] directions = Direction.values();
        JPanel[] edges = new JPanel[directions.length];
        for (int i = 0; i < directions.length; i++) {
            edges[i] = edgePanel(frame, directions[i]);
            layeredPane.add(edges[i], Integer.valueOf(JLayeredPane.DRAG_LAYER + 1));
        }
        frame.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                layoutEdges(frame, directions, edges);
            }

            @Override
            public void componentShown(ComponentEvent e) {
                layoutEdges(frame, directions, edges);
            }
        });
        layoutEdges(frame, directions, edges);
    }

    private static JPanel edgePanel(JFrame frame, Direction direction) {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setCursor(Cursor.getPredefinedCursor(direction.cursor));
        ResizeAdapter adapter = new ResizeAdapter(frame, direction);
        panel.addMouseListener(adapter);
        panel.addMouseMotionListener(adapter);
        return panel;
    }

    private static void layoutEdges(JFrame frame, Direction[] directions, JPanel[] edges) {
        int width = frame.getWidth();
        int height = frame.getHeight();
        int t = EDGE_THICKNESS;
        for (int i = 0; i < directions.length; i++) {
            JPanel edge = edges[i];
            switch (directions[i]) {
                case NORTH: edge.setBounds(0, 0, width, t); break;
                case SOUTH: edge.setBounds(0, height - t, width, t); break;
                case WEST: edge.setBounds(0, 0, t, height); break;
                case EAST: edge.setBounds(width - t, 0, t, height); break;
                case NORTH_WEST: edge.setBounds(0, 0, t, t); break;
                case NORTH_EAST: edge.setBounds(width - t, 0, t, t); break;
                case SOUTH_WEST: edge.setBounds(0, height - t, t, t); break;
                case SOUTH_EAST: edge.setBounds(width - t, height - t, t, t); break;
                default: break;
            }
        }
    }

    private enum Direction {
        NORTH(Cursor.N_RESIZE_CURSOR, true, false),
        SOUTH(Cursor.S_RESIZE_CURSOR, true, false),
        WEST(Cursor.W_RESIZE_CURSOR, false, true),
        EAST(Cursor.E_RESIZE_CURSOR, false, true),
        NORTH_WEST(Cursor.NW_RESIZE_CURSOR, true, true),
        NORTH_EAST(Cursor.NE_RESIZE_CURSOR, true, true),
        SOUTH_WEST(Cursor.SW_RESIZE_CURSOR, true, true),
        SOUTH_EAST(Cursor.SE_RESIZE_CURSOR, true, true);

        final int cursor;
        final boolean vertical;
        final boolean horizontal;

        Direction(int cursor, boolean vertical, boolean horizontal) {
            this.cursor = cursor;
            this.vertical = vertical;
            this.horizontal = horizontal;
        }
    }

    private static final class DragAdapter extends MouseAdapter {
        private final JFrame frame;
        private Point grabWindowLocation;
        private Point grabScreenLocation;

        DragAdapter(JFrame frame) {
            this.frame = frame;
        }

        @Override
        public void mousePressed(MouseEvent e) {
            if (e.getButton() != MouseEvent.BUTTON1) {
                return;
            }
            grabScreenLocation = MouseInfo.getPointerInfo().getLocation();
            grabWindowLocation = frame.getLocation();
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            if (grabScreenLocation == null || grabWindowLocation == null) {
                return;
            }
            Point current = MouseInfo.getPointerInfo().getLocation();
            frame.setLocation(grabWindowLocation.x + current.x - grabScreenLocation.x,
                    grabWindowLocation.y + current.y - grabScreenLocation.y);
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            grabScreenLocation = null;
            grabWindowLocation = null;
        }
    }

    private static final class ResizeAdapter extends MouseAdapter {
        private final JFrame frame;
        private final Direction direction;
        private Point startScreen;
        private Point startBounds;
        private Dimension startSize;

        ResizeAdapter(JFrame frame, Direction direction) {
            this.frame = frame;
            this.direction = direction;
        }

        @Override
        public void mousePressed(MouseEvent e) {
            if (e.getButton() != MouseEvent.BUTTON1) {
                return;
            }
            UiFactory.setLiveResize(true);
            startScreen = MouseInfo.getPointerInfo().getLocation();
            startBounds = frame.getLocation();
            startSize = frame.getSize();
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            if (startScreen == null || startSize == null) {
                return;
            }
            if (!SwingUtilities.isEventDispatchThread()) {
                return;
            }
            Point current = MouseInfo.getPointerInfo().getLocation();
            int dx = current.x - startScreen.x;
            int dy = current.y - startScreen.y;
            Dimension minimum = frame.getMinimumSize();
            int x = startBounds.x;
            int y = startBounds.y;
            int width = startSize.width;
            int height = startSize.height;

            if (direction.horizontal) {
                if (direction == Direction.WEST || direction == Direction.NORTH_WEST
                        || direction == Direction.SOUTH_WEST) {
                    int proposed = startSize.width - dx;
                    if (proposed >= minimum.width) {
                        x = startBounds.x + dx;
                        width = proposed;
                    }
                } else {
                    width = Math.max(minimum.width, startSize.width + dx);
                }
            }
            if (direction.vertical) {
                if (direction == Direction.NORTH || direction == Direction.NORTH_WEST
                        || direction == Direction.NORTH_EAST) {
                    int proposed = startSize.height - dy;
                    if (proposed >= minimum.height) {
                        y = startBounds.y + dy;
                        height = proposed;
                    }
                } else {
                    height = Math.max(minimum.height, startSize.height + dy);
                }
            }
            frame.setBounds(x, y, width, height);
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            UiFactory.setLiveResize(false);
            startScreen = null;
            startBounds = null;
            startSize = null;
        }
    }
}

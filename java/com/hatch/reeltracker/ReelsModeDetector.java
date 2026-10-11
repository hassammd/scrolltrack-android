package com.hatch.reeltracker;

import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Detects whether the visible screen is a Reels viewer.
 * Rules adapted from NeverScroll's tested FeedDetector (Instagram 448.x):
 *  - view id contains "reel_viewer" or "clips_viewer", or
 *  - engagement labels (like/comment/share) + selected tab label "reels", or
 *  - 2+ of like/comment/share controls forming a vertical right-side rail
 *    (reels UIs stack them vertically; feed action rows are horizontal).
 */
public class ReelsModeDetector {

    private static class Rail {
        int action; // 0=like 1=comment 2=share
        Rect b;
        Rail(int a, Rect r) { action = a; b = r; }
    }

    public static boolean isReelsMode(AccessibilityNodeInfo root, String pkg) {
        if (root == null) return false;
        try {
            Set<String> ids = new HashSet<>();
            Set<String> labels = new HashSet<>();
            Set<String> selected = new HashSet<>();
            List<Rail> rails = new ArrayList<>();
            Rect screen = new Rect();
            root.getBoundsInScreen(screen);
            if (screen.width() <= 0 || screen.height() <= 0) return false;

            ArrayDeque<AccessibilityNodeInfo> q = new ArrayDeque<>();
            q.add(root);
            Rect r = new Rect();
            int visited = 0;
            while (!q.isEmpty() && visited++ < 300) {
                AccessibilityNodeInfo n = q.poll();
                if (n == null) continue;
                boolean visible = true;
                try { visible = n.isVisibleToUser(); } catch (Exception ignored) {}
                if (visible) {
                    String id = safe(n.getViewIdResourceName()).toLowerCase(Locale.ROOT);
                    String tx = safe(n.getText()).toLowerCase(Locale.ROOT);
                    String cd = safe(n.getContentDescription()).toLowerCase(Locale.ROOT);
                    if (!id.isEmpty()) ids.add(id);
                    if (!tx.isEmpty()) labels.add(tx);
                    if (!cd.isEmpty()) labels.add(cd);
                    int act = railAction(id + " " + tx + " " + cd);
                    if (act >= 0) {
                        try {
                            n.getBoundsInScreen(r);
                            if (isRightRail(screen, r)) rails.add(new Rail(act, new Rect(r)));
                        } catch (Exception ignored) {}
                    }
                    try {
                        if (n.isSelected()) {
                            if (!tx.isEmpty()) selected.add(tx);
                            if (!cd.isEmpty()) selected.add(cd);
                        }
                    } catch (Exception ignored) {}
                }
                int kids = 0;
                try { kids = n.getChildCount(); } catch (Exception ignored) {}
                for (int i = 0; i < kids; i++) {
                    AccessibilityNodeInfo c = null;
                    try { c = n.getChild(i); } catch (Exception ignored) {}
                    if (c != null) q.add(c);
                }
                if (n != root) { try { n.recycle(); } catch (Exception ignored) {} }
            }

            int railActions = countVerticalRailActions(screen, rails);
            boolean engagement = containsAny(labels, "like", "comment", "share");
            boolean reelsTab = containsSub(selected, "reels");

            if (ReelAccessibilityService.IG.equals(pkg)) {
                return containsSub(ids, "reel_viewer") || containsSub(ids, "clips_viewer")
                        || (engagement && reelsTab) || railActions >= 2;
            }
            if (ReelAccessibilityService.isTikTok(pkg)) {
                boolean forYou = containsSub(labels, "for you") || containsSub(ids, "feed_video");
                return (forYou && engagement) || railActions >= 2;
            }
            // Facebook
            return containsSub(ids, "reel_viewer") || containsSub(ids, "clips_viewer")
                    || railActions >= 2;
        } catch (Exception e) {
            return false;
        }
    }

    private static int railAction(String label) {
        if (label.contains("like") && !label.contains("dislike")) return 0;
        if (label.contains("comment")) return 1;
        if (label.contains("share")) return 2;
        return -1;
    }

    private static boolean isRightRail(Rect screen, Rect r) {
        int w = screen.width(), h = screen.height();
        if (w <= 0 || h <= w) return false; // portrait only
        if (r.width() <= 0 || r.height() <= 0) return false;
        if (r.width() > w * 0.34) return false;
        int cx = r.centerX(), cy = r.centerY();
        return cx >= screen.left + (int) (w * 0.70)
                && cy >= screen.top + (int) (h * 0.18)
                && cy <= screen.top + (int) (h * 0.91);
    }

    private static int countVerticalRailActions(Rect screen, List<Rail> rails) {
        Set<Integer> actions = new HashSet<>();
        int w = screen.width(), h = screen.height();
        for (int i = 0; i < rails.size(); i++) {
            for (int j = i + 1; j < rails.size(); j++) {
                Rail a = rails.get(i), b = rails.get(j);
                if (a.action == b.action) continue;
                int dx = Math.abs(a.b.centerX() - b.b.centerX());
                int dy = Math.abs(a.b.centerY() - b.b.centerY());
                if (dx <= w * 0.12
                        && dy > Math.max(dx, Math.max(a.b.height(), b.b.height()))
                        && dy <= h * 0.35) {
                    actions.add(a.action);
                    actions.add(b.action);
                }
            }
        }
        return actions.size();
    }

    private static boolean containsSub(Set<String> set, String sub) {
        for (String s : set) if (s.contains(sub)) return true;
        return false;
    }

    private static boolean containsAny(Set<String> set, String... subs) {
        for (String s : set)
            for (String sub : subs)
                if (s.contains(sub)) return true;
        return false;
    }

    private static String safe(CharSequence c) {
        return c == null ? "" : c.toString();
    }
}

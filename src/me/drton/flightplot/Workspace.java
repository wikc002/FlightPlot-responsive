package me.drton.flightplot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The single owner of open analysis pages and the current-page pointer. */
final class Workspace {
    private final List<ChartTab> tabs = new ArrayList<ChartTab>();
    private int current = -1;

    List<ChartTab> tabs() { return tabs; }
    List<ChartTab> view() { return Collections.unmodifiableList(tabs); }
    int size() { return tabs.size(); }
    int currentIndex() { return current; }
    ChartTab current() { return current >= 0 && current < tabs.size() ? tabs.get(current) : null; }
    int indexOf(ChartTab tab) { return tabs.indexOf(tab); }
    boolean contains(ChartTab tab) { return tabs.contains(tab); }

    int add(ChartTab tab) {
        tabs.add(tab);
        current = tabs.size() - 1;
        return current;
    }

    int remove(ChartTab tab) {
        int index = tabs.indexOf(tab);
        if (index < 0) return current;
        tabs.remove(index);
        if (tabs.isEmpty()) current = -1;
        else if (current > index) current--;
        else if (current >= tabs.size()) current = tabs.size() - 1;
        return current;
    }

    void select(int index) {
        if (index < 0 || index >= tabs.size()) throw new IndexOutOfBoundsException("tab " + index);
        current = index;
    }

    void closeAll() {
        for (ChartTab tab : new ArrayList<ChartTab>(tabs)) tab.closeLogReader();
        tabs.clear();
        current = -1;
    }
}

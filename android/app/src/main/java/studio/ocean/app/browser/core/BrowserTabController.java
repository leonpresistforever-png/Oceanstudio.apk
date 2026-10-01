package studio.ocean.app.browser.core;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import studio.ocean.app.browser.BrowserTab;

/**
 * Controller for browser tabs. Manages tab lifecycle, pinned tabs, locked tabs,
 * tab duplication, search, and recently closed tab restoration.
 * Adheres strictly to Ocean Privacy Directive (PDF 4 §10.1, §10.2).
 */
public final class BrowserTabController {
    private static final int MAX_RECENTLY_CLOSED = 15;

    private final List<BrowserTab> tabs = new ArrayList<>();
    private final LinkedList<BrowserTab> recentlyClosed = new LinkedList<>();
    private int currentTabIndex = 0;

    public BrowserTabController() {
    }

    public synchronized int getTabCount() {
        return tabs.size();
    }

    public synchronized int getCurrentTabIndex() {
        return currentTabIndex;
    }

    @Nullable
    public synchronized BrowserTab getCurrentTab() {
        if (tabs.isEmpty() || currentTabIndex < 0 || currentTabIndex >= tabs.size()) {
            return null;
        }
        return tabs.get(currentTabIndex);
    }

    @Nullable
    public synchronized BrowserTab getTab(int index) {
        if (index < 0 || index >= tabs.size()) {
            return null;
        }
        return tabs.get(index);
    }

    @NonNull
    public synchronized List<BrowserTab> getAllTabs() {
        return Collections.unmodifiableList(new ArrayList<>(tabs));
    }

    public synchronized int addTab(@NonNull String url) {
        return addTab(new BrowserTab(url));
    }

    public synchronized int addTab(@NonNull BrowserTab tab) {
        tabs.add(tab);
        currentTabIndex = tabs.size() - 1;
        return currentTabIndex;
    }

    public synchronized void selectTab(int index) {
        if (index >= 0 && index < tabs.size()) {
            currentTabIndex = index;
        }
    }

    @Nullable
    public synchronized BrowserTab closeTab(int index) {
        if (index < 0 || index >= tabs.size()) {
            return null;
        }
        BrowserTab removed = tabs.remove(index);
        recentlyClosed.addFirst(removed.copy());
        while (recentlyClosed.size() > MAX_RECENTLY_CLOSED) {
            recentlyClosed.removeLast();
        }

        if (tabs.isEmpty()) {
            currentTabIndex = -1;
        } else if (currentTabIndex >= tabs.size()) {
            currentTabIndex = tabs.size() - 1;
        }
        return removed;
    }

    @Nullable
    public synchronized BrowserTab reopenLastClosedTab() {
        if (recentlyClosed.isEmpty()) {
            return null;
        }
        BrowserTab restored = recentlyClosed.removeFirst();
        addTab(restored);
        return restored;
    }

    public synchronized int getRecentlyClosedCount() {
        return recentlyClosed.size();
    }

    public synchronized int duplicateTab(int index) {
        BrowserTab source = getTab(index);
        if (source == null) return -1;
        BrowserTab copy = source.copy();
        tabs.add(index + 1, copy);
        currentTabIndex = index + 1;
        return currentTabIndex;
    }

    public synchronized void setPinned(int index, boolean pinned) {
        BrowserTab tab = getTab(index);
        if (tab != null) {
            tab.pinned = pinned;
        }
    }

    public synchronized void setLocked(int index, boolean locked) {
        BrowserTab tab = getTab(index);
        if (tab != null) {
            tab.locked = locked;
        }
    }

    @NonNull
    public synchronized List<BrowserTab> searchTabs(@NonNull String query) {
        String lower = query.toLowerCase(Locale.ROOT).trim();
        if (lower.isEmpty()) {
            return getAllTabs();
        }
        List<BrowserTab> matches = new ArrayList<>();
        for (BrowserTab tab : tabs) {
            if ((tab.title != null && tab.title.toLowerCase(Locale.ROOT).contains(lower))
                    || (tab.url != null && tab.url.toLowerCase(Locale.ROOT).contains(lower))) {
                matches.add(tab);
            }
        }
        return matches;
    }

    public synchronized void clearAllTabs() {
        tabs.clear();
        recentlyClosed.clear();
        currentTabIndex = -1;
    }
}

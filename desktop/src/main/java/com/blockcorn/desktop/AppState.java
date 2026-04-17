package com.blockcorn.desktop;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Thread-safe shared application state. */
public final class AppState {

    private volatile boolean enabled = true;
    private volatile List<String> domains = Collections.emptyList();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean v) { enabled = v; }

    public List<String> getDomains() { return domains; }
    public void setDomains(List<String> d) { domains = new ArrayList<>(d); }
}

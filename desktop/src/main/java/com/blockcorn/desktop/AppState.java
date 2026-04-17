package com.blockcorn.desktop;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Thread-safe shared application state. */
public final class AppState {

    private volatile boolean enabled = false; // off until user clicks Enable
    private volatile List<String> domains = Collections.emptyList();
    private volatile boolean domainsReady = false;

    public boolean isEnabled()  { return enabled; }
    public void setEnabled(boolean v) { enabled = v; }

    public List<String> getDomains()   { return domains; }
    public boolean isDomainsReady()    { return domainsReady; }
    public void setDomains(List<String> d) {
        domains = new ArrayList<>(d);
        domainsReady = true;
    }
}

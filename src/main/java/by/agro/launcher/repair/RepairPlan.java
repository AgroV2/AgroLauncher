package by.agro.launcher.repair;

import java.util.ArrayList;
import java.util.List;

public final class RepairPlan {
    public String transactionId;
    public List<RepairAction> actions = new ArrayList<>();

    public boolean isEmpty() { return actions.isEmpty(); }
}

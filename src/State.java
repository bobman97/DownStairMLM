import org.dreambot.api.methods.container.impl.Inventory;
import org.dreambot.api.methods.map.Area;

public enum State {
    WALKING_TO_VEINS, FINDING_VEIN, MINING_VEIN,
    WALKING_TO_HOPPER, DEPOSITING,
    WALKING_TO_SACK, CLAIMING,
    WALKING_TO_BANK, USE_BANK, BANKING;
}

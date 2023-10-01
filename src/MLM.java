import org.dreambot.api.methods.container.impl.Inventory;
import org.dreambot.api.methods.depositbox.DepositBox;
import org.dreambot.api.methods.input.mouse.MouseSettings;
import org.dreambot.api.methods.interactive.GameObjects;
import org.dreambot.api.methods.interactive.Players;
import org.dreambot.api.methods.map.Area;
import org.dreambot.api.methods.map.Tile;
import org.dreambot.api.methods.settings.PlayerSettings;
import org.dreambot.api.methods.walking.impl.Walking;
import org.dreambot.api.methods.world.World;
import org.dreambot.api.methods.world.Worlds;
import org.dreambot.api.methods.worldhopper.WorldHopper;
import org.dreambot.api.script.AbstractScript;
import org.dreambot.api.script.Category;
import org.dreambot.api.script.ScriptManifest;
import org.dreambot.api.script.listener.PaintListener;
import org.dreambot.api.utilities.Logger;
import org.dreambot.api.utilities.Sleep;
import org.dreambot.api.wrappers.interactive.GameObject;

import java.awt.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.text.NumberFormat;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.json.JSONException;
import org.json.JSONObject;

@ScriptManifest(name = "DownStairMLM", description = "Simple Script for lower level Motherlode Mine.", author = "sloppybot",
        version = 1.0, category = Category.MINING)
public class MLM extends AbstractScript implements PaintListener {
    State state;
    int[] oreValue;
    int paydirtInInventory, paydirtCounter, runiteOreCounter, coalOreCounter, goldOreCounter, mithrilOreCounter, adamantiteOreCounter, goldNuggetCounter;
    int profit, inventoryCapacity, sackType, sackCapacity;;
    long timeBegan;
    String timeRan, currentState;
    private static final int DEFAULT_SACK_CAPACITY = 81;
    private static final int DEFAULT_SACK_ID = 5558;
    private static final int UPGRADED_SACK_CAPACITY = 162;
    private static final int UPGRADED_SACK_ID = 5556;
    private static final int NUGGET_VALUE = 1230;
    private static final int COAL_VALUE = 245;
    private static final int GOLD_VALUE = 201;
    private static final int MITHRIL_VALUE = 90;
    private static final int ADAMANTITE_VALUE = 788;
    private static final int RUNITE_VALUE = 10891;
    private static URLConnection con;
    private static InputStream is;
    private static InputStreamReader isr;
    private static BufferedReader br;

    Area mlmArea = new Area(3774, 5633, 3713, 5693);
    Area veinArea, bankArea, hopperArea, sackArea;
    Random random;

    @Override
    public void onStart() {
        random = new Random();
         currentState = "Thinking.";
         veinArea = new Area(3729, 5669, 3757, 5646);
         bankArea = new Area(3759, 5665, 3757, 5663);
         hopperArea = new Area(3748, 5674, 3752, 5670);
         sackArea = new Area(3748, 5662, 3752, 5658);

         sackType = PlayerSettings.getBitValue(DEFAULT_SACK_ID) >= 0 ? DEFAULT_SACK_ID : UPGRADED_SACK_ID;
         sackCapacity = PlayerSettings.getBitValue(DEFAULT_SACK_ID) >= 0 ? DEFAULT_SACK_CAPACITY : UPGRADED_SACK_CAPACITY;

         paydirtInInventory = paydirtCounter = coalOreCounter = goldOreCounter = mithrilOreCounter
                 = adamantiteOreCounter = runiteOreCounter = goldNuggetCounter = profit = 0;

         timeBegan = System.currentTimeMillis();
         inventoryCapacity = getInventoryCapacityCount();
         state = State.WALKING_TO_VEINS;
         oreValue = new int[]{NUGGET_VALUE, COAL_VALUE, GOLD_VALUE, MITHRIL_VALUE, ADAMANTITE_VALUE, RUNITE_VALUE};
         getOreValue();
    }

    @Override
    public int onLoop() {
        MouseSettings.setSpeed(random.nextInt(80-20) + 20);
        int paydirtCount = Inventory.count("Pay-dirt");
        if (paydirtCount > paydirtInInventory) {
            paydirtCounter += paydirtCount - paydirtInInventory;
            paydirtInInventory = paydirtCount;
        }
        // Actions
        Sleep.sleepUntil(this::isPlayerBusy, 1000);

        if(!isPlayerBusy()) {
            State newState = getState();
            switch (newState) {
                case WALKING_TO_BANK:
                    currentState = "Walking to Bank.";
                    Walking.walk(bankArea.getRandomTile());
                    break;

                case USE_BANK:
                    currentState = "Banking.";
                    GameObject bankBooth = GameObjects.closest("Bank deposit box");
                    if (bankBooth != null && bankBooth.interact("Deposit"))
                        Sleep.sleepUntil(DepositBox::isOpen, 4000);
                    break;

                case BANKING:
                    if (Inventory.contains(item -> item.getName().contains("pickaxe")))
                        DepositBox.depositAllExcept(item -> item.getName().contains("pickaxe") || item.getName().equalsIgnoreCase("pay-dirt"));
                    else
                        DepositBox.depositAllExcept("Pay-dirt");
                    Sleep.sleepUntil(() -> Inventory.onlyContains(item -> item.getName().contains("pickaxe")) || Inventory.isEmpty(), 2000);
                    if (Inventory.onlyContains(item -> item.getName().contains("pickaxe") || item.getName().equalsIgnoreCase("pay-dirt")) || Inventory.isEmpty())
                        DepositBox.close();
                    break;

                case WALKING_TO_VEINS:
                    currentState = "Walking to Ore veins.";
                    if (!Players.getLocal().isMoving()) {
                        Walking.walk(veinArea.getRandomTile());
                    }
                    break;

                case FINDING_VEIN:
                    currentState = "Mining Ore veins.";
                    GameObject vein = GameObjects.closest(v -> v.getName().equalsIgnoreCase("ore vein") && veinArea.contains(v));
                    if (vein != null && vein.interact("Mine")) {
                        Sleep.sleepUntil(() -> !vein.exists(), 15000);
                    }
                    break;
                case WALKING_TO_HOPPER:
                    currentState = "Walking to Hopper.";
                    Walking.walk(hopperArea.getRandomTile());
                    break;

                case DEPOSITING:
                    while(shouldWorldSwitch()) { // CHECKING IF STRUT IS WORKING BEFORE DEPOSITING PAY-DIRT
                        currentState = "Hopping worlds.";
                        World world = Worlds.getRandomWorld(w -> !w.isF2P() && !w.isPVP() && w.getMinimumLevel() == 0 && !w.isBeta() && w.isNormal());
                        WorldHopper.hopWorld(world);
                    }

                    currentState = "Depositing Hopper.";
                    GameObject deposit = GameObjects.closest("Hopper");
                    if (deposit != null && deposit.interact("Deposit")) {
                        currentState = "Cleaning Pay-dirt.";
                        Sleep.sleepUntil(() -> Players.getLocal().isAnimating(), 1000);
                        paydirtInInventory = Inventory.count("Pay-dirt");
                        Sleep.sleepUntil(this::shouldWorldSwitch, 8000); // Sleeping HERE -> STRUT CAN BREAK DURING THIS REST.
                    }
                    break;

                case WALKING_TO_SACK:
                    currentState = "Walking to Sack.";
                    Walking.walk(sackArea.getRandomTile());
                    break;

                case CLAIMING:
                    currentState = "Emptying Sack.";
                    GameObject sack = GameObjects.closest("Sack");
                    if (sack != null && sack.interact("Search")) {
                        Sleep.sleepUntil(() -> Inventory.contains(i -> i.getName().contains("ore") || i.getName().contains("nugget")), 2000);
                        runiteOreCounter += Inventory.count("Runite ore");
                        coalOreCounter += Inventory.count("Coal ore");
                        goldOreCounter += Inventory.count("Gold ore");
                        adamantiteOreCounter += Inventory.count("Adamantite ore");
                        mithrilOreCounter += Inventory.count("Mithril ore");
                        goldNuggetCounter += Inventory.count("Golden nugget");
                    }
                    break;
                default:
                    currentState = "Thinking.";
                    break;
            }
        }
        return 1;
    }

    private State getState() {
        // Should walk to Bank Deposit?
        if (shouldWalkToDepositbox())
            return State.WALKING_TO_BANK;

        // Should Use On Bank Deposit
        if (shouldUseBankDepositbox())
            return State.USE_BANK;

        // Should Deposit Items In Bank
        if (shouldDepositItems())
            return State.BANKING;

            // Should Walk to Sack
        if (shouldWalkToSack())
            return State.WALKING_TO_SACK;

            // Should Claim Sack
        if (shouldClaimSack())
            return State.CLAIMING;

            // Should walk to hopper
        if (shouldWalkToHopper())
            return State.WALKING_TO_HOPPER;

            // Should Deposit Hopper
        if(shouldDepositHopper())
            return State.DEPOSITING;

        // Should Walk To Veins
        if (shouldWalkToOreVein())
            return State.WALKING_TO_VEINS;

        // Should Locate Vein
        if (shouldFindOreVein())
            return State.FINDING_VEIN;

        // Should Mine Vein
        if (shouldMineOreVein())
            return State.MINING_VEIN;

        currentState = "Thinking.";
        return state;
    }


    private int getSackCount() {
        return PlayerSettings.getBitValue(sackType);
    }

    private int getInventoryPaydirtCount() {
        return Inventory.count("Pay-dirt");
    }

    private int getInventoryCount() {
        return (inventoryCapacity == 27 ? Inventory.fullSlotCount() - 1 : Inventory.fullSlotCount());
    }

    private int getInventoryCapacityCount() {
        return Inventory.capacity() - Inventory.count(i -> i.getName().contains("pickaxe"));
    }

    private boolean shouldWalkToOreVein() {
        return getSackCount() < sackCapacity && !isPlayerInArea(veinArea) && getInventoryCount() < getInventoryCapacityCount();
    }

    private boolean shouldWalkToDepositbox() {
        boolean inventoryFullOrSackEmpty = (getInventoryCount() >= inventoryCapacity || getSackCount() == 0) && getInventoryPaydirtCount() != getInventoryCount();
        return inventoryFullOrSackEmpty && (isPlayerInArea(sackArea) || !isPlayerInArea(bankArea));
    }

    private boolean shouldWalkToHopper() {
        return (getInventoryCount() >= inventoryCapacity || ((getInventoryPaydirtCount() + getSackCount()) >= sackCapacity)) && getInventoryPaydirtCount() > 0 && !isPlayerInArea(hopperArea);
    }

    private boolean shouldWalkToSack() {
        return getSackCount() >= sackCapacity && !isPlayerInArea(sackArea);
    }

    private boolean shouldUseBankDepositbox() {
        return !DepositBox.isOpen() && getInventoryCount() >= inventoryCapacity
                && getInventoryPaydirtCount() < getInventoryCount()
                && isPlayerInArea(bankArea);
    }

    private boolean shouldDepositItems() {
        return DepositBox.isOpen() && getInventoryCount() >= inventoryCapacity && (getInventoryPaydirtCount() != inventoryCapacity);
    }

    private boolean shouldFindOreVein() {
        return !shouldWalkToSack() && getInventoryCount() < inventoryCapacity && isPlayerInArea(veinArea);
    }

    private boolean shouldMineOreVein() {
        return shouldFindOreVein() && getSackCount() + getInventoryPaydirtCount() < sackCapacity;
    }

    private boolean shouldDepositHopper() {

        return ((getInventoryCount() >= inventoryCapacity) || (getSackCount() + getInventoryPaydirtCount() >= sackCapacity)) && isPlayerInArea(hopperArea);
    }

    private boolean shouldClaimSack() {
        return getInventoryCount() < inventoryCapacity && getSackCount() >= sackCapacity && isPlayerInArea(sackArea);
    }

    private Tile getPlayerPosition() {
        return Players.getLocal().getTile();
    }

    private boolean isPlayerInArea(Area area) {
        return area.contains(getPlayerPosition());
    }

    private boolean isPlayerBusy() {
        return Players.getLocal().isAnimating() || Players.getLocal().isMoving();
    }

    @Override
    public void onPaint(Graphics g) {
        timeRan = millisToDuration(System.currentTimeMillis() - this.timeBegan);
        profit = (goldNuggetCounter * oreValue[0]) + (coalOreCounter * oreValue[1]) + (goldOreCounter * oreValue[2])
                + (mithrilOreCounter * oreValue[3]) + (adamantiteOreCounter * oreValue[4]) + (runiteOreCounter * oreValue[5]);

        g.setColor(new Color(30, 30, 30, 100));
        g.fillRect(5, 100, 200, 70);

        drawLabel(g, "Pay-dirt:", Color.white, 10, 115);
        drawValue(g, String.valueOf(paydirtCounter), Color.BLACK, 60, 115);

        drawLabel(g, "Run time:", Color.white, 90, 115);
        drawValue(g, timeRan, Color.GRAY, 145, 115);

        String[] stats = {"Runite ore:", "Profit:", "Gold Nugget:", "Current State:"};
        int[] statsX = {10, 90, 10, 10};
        int[] statsY = {130, 130, 145, 160};
        Color[] oreColors = {Color.cyan, Color.green, Color.orange, Color.magenta};
        String[] oreValues = {
                String.valueOf(runiteOreCounter),
                NumberFormat.getNumberInstance().format(profit),
                String.valueOf(goldNuggetCounter),
                currentState
        };

        for (int i = 0; i < stats.length; i++) {
            drawLabel(g, stats[i], Color.white, statsX[i], statsY[i]);

            // Get the width of the label
            int labelWidth = g.getFontMetrics().stringWidth(stats[i]);

            // Adjust the x position for the value based on the width of the label
            drawValue(g, oreValues[i], oreColors[i], statsX[i] + labelWidth + 5, statsY[i]); // +5 for a small gap
        }

    }

    private void drawLabel(Graphics g, String text, Color color, int x, int y) {
        g.setColor(color);
        g.drawString(text, x, y);
    }

    private void drawValue(Graphics g, String text, Color color, int x, int y) {
        g.setColor(color);
        g.drawString(text, x, y);
    }

    private static String millisToDuration(long millis) {
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % TimeUnit.HOURS.toMinutes(1);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % TimeUnit.MINUTES.toSeconds(1);
        long milliseconds = millis % TimeUnit.SECONDS.toMillis(1);

        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }


    private boolean shouldWorldSwitch() {
        List<GameObject> struts = GameObjects.all(s -> s.getName().equalsIgnoreCase("broken strut"));
        return struts.size() > 1;
    }

    private void getOreValue() {
        String[] ores = {"1761", "453", "444", "447", "449", "451"};
        for (int i = 0; i < ores.length; i++) {
            try {
                JSONObject oreData = fetchOreData(ores[i]);
                oreValue[i] = calculateMedian(oreData);
            } catch (Exception e) {
                Logger.log(e.getMessage());
            }
        }
        oreValue[0] *= 10; // 10 soft clays = 1 golden nugget.
    }

    private JSONObject fetchOreData(String oreId) throws IOException {
        URL url = new URL("https://prices.runescape.wiki/api/v1/osrs/latest?id=" + oreId);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.3");

        try (InputStream is = con.getInputStream();
             InputStreamReader isr = new InputStreamReader(is);
             BufferedReader br = new BufferedReader(isr)) {

            StringBuilder response = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                response.append(line);
            }
            JSONObject jsonResponse = new JSONObject(response.toString());
            return jsonResponse.getJSONObject("data").getJSONObject(oreId);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private int calculateMedian(JSONObject oreData) throws JSONException {
        int high = oreData.getInt("high");
        int low = oreData.getInt("low");
        return (high + low) / 2;
    }

}

package pk.edu.nfciet.shopease;

import java.text.NumberFormat;
import java.util.Locale;

/** Single source of truth for all rupee amounts and Assignment 2 delivery policy. */
public final class ShopMoney {
    private ShopMoney() { }
    public static String rs(long amount) {
        return "Rs " + NumberFormat.getIntegerInstance(Locale.forLanguageTag("en-PK")).format(amount);
    }
    public static long delivery(long subtotal) {
        return subtotal == 0 || subtotal >= 5000 ? 0 : 200;
    }
    public static String deliveryLabel(long subtotal) {
        return delivery(subtotal) == 0 ? "Free" : rs(delivery(subtotal));
    }
}

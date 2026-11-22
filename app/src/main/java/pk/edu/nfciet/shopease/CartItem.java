package pk.edu.nfciet.shopease;

import com.google.firebase.firestore.DocumentId;

/** Firestore model for /carts/{uid}/items/{productId}. */
public class CartItem {
    @DocumentId public String id;
    public String title, imageUrl;
    public long price, quantity;
    public CartItem() { }
}

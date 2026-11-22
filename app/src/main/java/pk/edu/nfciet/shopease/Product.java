package pk.edu.nfciet.shopease;

import com.google.firebase.firestore.DocumentId;

/** Firestore model for documents in /products. Firebase needs a public no-arg constructor. */
public class Product {
    @DocumentId public String id;
    public String title, description, category, imageUrl;
    public long price, stock;
    public Product() { }
}

package pk.edu.nfciet.shopease;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.text.Editable;
import android.text.TextWatcher;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Source;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import com.google.android.material.textfield.TextInputEditText;

/** Assignment 2: real, filterable two-column Firestore catalog. */
public class ProductListActivity extends AppCompatActivity {
    private ProductAdapter adapter;
    private View progress, statePanel, btnRetry;
    private TextView stateTitle, stateBody, countText;
    private TextInputEditText search;
    private ChipGroup categoryGroup;
    private final List<Product> products = new ArrayList<>();
    private String category = "All";
    private boolean loaded;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppUi.lightBars(this);
        setContentView(R.layout.activity_product_list);
        if (!AppUi.isFirebaseReady(this) || FirebaseAuth.getInstance().getCurrentUser() == null) {
            AppUi.goAndClear(this, LoginActivity.class); return;
        }
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setTitle("");
        progress = findViewById(R.id.progress);
        statePanel = findViewById(R.id.statePanel);
        btnRetry = findViewById(R.id.btnRetry);
        stateTitle = findViewById(R.id.tvStateTitle);
        stateBody = findViewById(R.id.tvStateBody);
        countText = findViewById(R.id.tvProductCount);
        search = findViewById(R.id.etSearch);
        categoryGroup = findViewById(R.id.categoryGroup);
        RecyclerView grid = findViewById(R.id.rvProducts);
        grid.setLayoutManager(new GridLayoutManager(this, 2));
        grid.setHasFixedSize(false);
        adapter = new ProductAdapter(product -> {
            if (product.id == null) return;
            Intent intent = new Intent(this, ProductDetailActivity.class);
            intent.putExtra("productId", product.id); // pass only the Firestore document ID
            startActivity(intent);
        });
        grid.setAdapter(adapter);
        btnRetry.setOnClickListener(v -> loadProducts());
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filterProducts(); }
            @Override public void afterTextChanged(Editable s) { }
        });
        categoryGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            int checked = checkedIds.isEmpty() ? R.id.chipAll : checkedIds.get(0);
            if (checked == R.id.chipClothing) category = "Clothing";
            else if (checked == R.id.chipElectronics) category = "Electronics";
            else if (checked == R.id.chipGrocery) category = "Grocery";
            else category = "All";
            filterProducts();
        });
        loadGreeting();
    }

    @Override protected void onResume() {
        super.onResume();
        if (adapter != null && FirebaseAuth.getInstance().getCurrentUser() != null) loadProducts();
    }

    private void loadGreeting() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        FirebaseFirestore.getInstance().collection("users").document(user.getUid()).get()
                .addOnSuccessListener(this, snapshot -> {
                    String name = snapshot.getString("name");
                    if (name != null && !name.trim().isEmpty()) {
                        String first = name.trim().split("\\s+")[0];
                        ((TextView) findViewById(R.id.tvGreeting)).setText("GOOD TO SEE YOU, " + first.toUpperCase(Locale.ROOT));
                    }
                }); // greeting is a friendly extra; catalog still works if profile read fails
    }

    private void loadProducts() {
        if (isFinishing()) return;
        loaded = false;
        progress.setVisibility(View.VISIBLE);
        statePanel.setVisibility(View.GONE);
        FirebaseFirestore.getInstance().collection("products").get(Source.SERVER)
                .addOnSuccessListener(this, snap -> {
                    loaded = true;
                    progress.setVisibility(View.GONE);
                    products.clear();
                    products.addAll(snap.toObjects(Product.class));
                    Collections.sort(products, (a, b) -> String.valueOf(a.title).compareToIgnoreCase(String.valueOf(b.title)));
                    filterProducts();
                })
                .addOnFailureListener(this, e -> {
                    FirestoreUiErrors.log("Read products", e);
                    loaded = false;
                    progress.setVisibility(View.GONE);
                    products.clear();
                    adapter.setItems(Collections.emptyList());
                    countText.setText("Products unavailable");
                    showState(FirestoreUiErrors.title(e, "Products"), FirestoreUiErrors.message(e), true);
                });
    }

    private void filterProducts() {
        if (!loaded || adapter == null) return;
        String query = search.getText() == null ? "" : search.getText().toString().trim().toLowerCase(Locale.ROOT);
        List<Product> filtered = new ArrayList<>();
        for (Product product : products) {
            boolean inCategory = category.equals("All") || category.equalsIgnoreCase(product.category);
            String haystack = (String.valueOf(product.title) + " " + String.valueOf(product.category) + " " + String.valueOf(product.description)).toLowerCase(Locale.ROOT);
            if (inCategory && haystack.contains(query)) filtered.add(product);
        }
        adapter.setItems(filtered);
        countText.setText(filtered.size() + (filtered.size() == 1 ? " curated find" : " curated finds"));
        if (filtered.isEmpty()) {
            showState(products.isEmpty() ? "No products yet" : "No matches, just yet",
                    products.isEmpty() ? "Add products to the Firestore products collection to start shopping." : "Try another search or browse All categories.",
                    products.isEmpty());
        } else statePanel.setVisibility(View.GONE);
    }

    private void showState(String title, String message, boolean canRetry) {
        stateTitle.setText(title);
        stateBody.setText(message);
        btnRetry.setVisibility(canRetry ? View.VISIBLE : View.GONE);
        statePanel.setVisibility(View.VISIBLE);
    }

    @Override public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }
    @Override public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_cart) {
            startActivity(new Intent(this, CartActivity.class)); return true;
        }
        if (item.getItemId() == R.id.action_logout) {
            FirebaseAuth.getInstance().signOut();
            AppUi.goAndClear(this, LoginActivity.class);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}

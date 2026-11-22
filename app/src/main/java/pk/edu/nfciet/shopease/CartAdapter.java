package pk.edu.nfciet.shopease;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Displays cart quantities and delegates all Firestore writes to CartActivity. */
public class CartAdapter extends RecyclerView.Adapter<CartAdapter.Holder> {
    public interface Actions {
        void changeQuantity(CartItem item, int delta);
        void remove(CartItem item);
    }
    private final List<CartItem> items = new ArrayList<>();
    private final Set<String> busy = new HashSet<>();
    private final Actions actions;
    public CartAdapter(Actions actions) { this.actions = actions; }

    public void setItems(List<CartItem> next) {
        items.clear(); items.addAll(next); notifyDataSetChanged();
    }
    public void setBusy(String id, boolean value) {
        if (value) busy.add(id); else busy.remove(id);
        for (int i = 0; i < items.size(); i++) if (id.equals(items.get(i).id)) { notifyItemChanged(i); break; }
    }
    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_cart, parent, false));
    }
    @Override public void onBindViewHolder(@NonNull Holder h, int position) {
        CartItem item = items.get(position);
        boolean waiting = busy.contains(item.id);
        h.title.setText(item.title == null ? "Product" : item.title);
        h.unitPrice.setText(ShopMoney.rs(item.price) + " each");
        h.linePrice.setText(ShopMoney.rs(item.price * item.quantity));
        h.quantity.setText(String.valueOf(item.quantity));
        Glide.with(h.image).load(item.imageUrl).centerCrop()
                .placeholder(R.drawable.bg_product_placeholder).error(R.drawable.bg_product_placeholder).into(h.image);
        h.minus.setEnabled(!waiting && item.quantity > 1);
        h.plus.setEnabled(!waiting);
        h.remove.setEnabled(!waiting);
        h.minus.setOnClickListener(v -> actions.changeQuantity(item, -1));
        h.plus.setOnClickListener(v -> actions.changeQuantity(item, 1));
        h.remove.setOnClickListener(v -> actions.remove(item));
    }
    @Override public int getItemCount() { return items.size(); }
    static class Holder extends RecyclerView.ViewHolder {
        ImageView image;
        TextView title, unitPrice, linePrice, quantity, remove;
        MaterialButton minus, plus;
        Holder(View v) {
            super(v);
            image = v.findViewById(R.id.ivImage);
            title = v.findViewById(R.id.tvTitle);
            unitPrice = v.findViewById(R.id.tvUnitPrice);
            linePrice = v.findViewById(R.id.tvLineTotal);
            quantity = v.findViewById(R.id.tvQty);
            remove = v.findViewById(R.id.tvRemove);
            minus = v.findViewById(R.id.btnMinus);
            plus = v.findViewById(R.id.btnPlus);
        }
    }
}

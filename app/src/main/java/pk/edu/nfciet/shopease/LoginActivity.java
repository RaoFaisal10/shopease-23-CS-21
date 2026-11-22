package pk.edu.nfciet.shopease;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;

/** Real Firebase Email/Password auth. The browser preview is a separate visual simulation. */
public class LoginActivity extends AppCompatActivity {
    private TextInputEditText emailField, passwordField;
    private TextInputLayout emailLayout, passwordLayout;
    private MaterialButton loginButton;
    private TextView forgotLink;
    private ProgressBar progress;
    private boolean sendingReset;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppUi.lightBars(this);
        setContentView(R.layout.activity_login);
        emailField = findViewById(R.id.etEmail);
        passwordField = findViewById(R.id.etPassword);
        emailLayout = findViewById(R.id.tilEmail);
        passwordLayout = findViewById(R.id.tilPassword);
        loginButton = findViewById(R.id.btnLogin);
        forgotLink = findViewById(R.id.tvForgot);
        progress = findViewById(R.id.progress);
        loginButton.setOnClickListener(v -> login());
        forgotLink.setOnClickListener(v -> resetPassword());
        findViewById(R.id.tvGoToSignUp).setOnClickListener(v ->
                startActivity(new Intent(this, SignUpActivity.class)));
    }

    private String entered(TextInputEditText field) {
        return field.getText() == null ? "" : field.getText().toString().trim();
    }
    private boolean validEmail(String email) {
        return Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }
    private void login() {
        String email = entered(emailField);
        String password = passwordField.getText() == null ? "" : passwordField.getText().toString();
        emailLayout.setError(null); passwordLayout.setError(null);
        if (!validEmail(email)) {
            emailLayout.setError(getString(R.string.err_email)); emailField.requestFocus(); return;
        }
        if (password.length() < 6) {
            passwordLayout.setError(getString(R.string.err_password)); passwordField.requestFocus(); return;
        }
        if (!AppUi.isFirebaseReady(this)) {
            AppUi.message(this, getString(R.string.config_missing)); return;
        }
        setLoading(true);
        FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(this, result -> AppUi.goAndClear(this, ProductListActivity.class))
                .addOnFailureListener(this, error -> {
                    setLoading(false);
                    AppUi.message(this, error.getLocalizedMessage() != null
                            ? error.getLocalizedMessage() : "Sign-in failed. Please try again.");
                });
    }
    private void resetPassword() {
        if (sendingReset) return;
        String email = entered(emailField);
        emailLayout.setError(null);
        if (!validEmail(email)) {
            emailLayout.setError(getString(R.string.err_email)); emailField.requestFocus(); return;
        }
        if (!AppUi.isFirebaseReady(this)) {
            AppUi.message(this, getString(R.string.config_missing)); return;
        }
        sendingReset = true;
        forgotLink.setEnabled(false);
        forgotLink.setText("Sending reset link…");
        setLoading(true);
        FirebaseAuth.getInstance().sendPasswordResetEmail(email)
                .addOnSuccessListener(this, unused -> {
                    restoreResetButton();
                    new MaterialAlertDialogBuilder(this)
                            .setTitle("Password reset link sent")
                            .setMessage("We've sent a password reset link to " + email
                                    + ".\n\nCheck your inbox and Spam folder, then follow the link to set a new password.")
                            .setPositiveButton("Got it", (dialog, which) -> dialog.dismiss())
                            .show();
                })
                .addOnFailureListener(this, error -> {
                    restoreResetButton();
                    new MaterialAlertDialogBuilder(this)
                            .setTitle("Couldn't send reset link")
                            .setMessage(error.getLocalizedMessage() != null
                                    ? error.getLocalizedMessage() : "Please check the email and try again.")
                            .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                            .show();
                });
    }
    private void restoreResetButton() {
        sendingReset = false;
        forgotLink.setEnabled(true);
        forgotLink.setText(R.string.forgot_password);
        setLoading(false);
    }
    private void setLoading(boolean loading) {
        loginButton.setEnabled(!loading);
        forgotLink.setEnabled(!loading);
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
    }
}

package pk.edu.nfciet.shopease;

import android.util.Log;
import com.google.firebase.firestore.FirebaseFirestoreException;

/** Turns Firestore diagnostics into safe, actionable messages for Assignment 2. */
public final class FirestoreUiErrors {
    private FirestoreUiErrors() { }

    public static void log(String operation, Exception error) {
        Log.e("ShopEaseFirestore", operation + " failed", error);
    }

    public static String title(Exception error, String feature) {
        FirebaseFirestoreException.Code code = code(error);
        if (code == FirebaseFirestoreException.Code.PERMISSION_DENIED) return feature + " access blocked";
        if (code == FirebaseFirestoreException.Code.UNAUTHENTICATED) return "Sign in again";
        if (code == FirebaseFirestoreException.Code.NOT_FOUND) return "Firestore database unavailable";
        if (code == FirebaseFirestoreException.Code.UNAVAILABLE
                || code == FirebaseFirestoreException.Code.DEADLINE_EXCEEDED) return "Connection unavailable";
        return "Couldn't load " + feature.toLowerCase(java.util.Locale.ROOT);
    }

    public static String message(Exception error) {
        FirebaseFirestoreException.Code code = code(error);
        if (code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
            return "Firestore denied access (PERMISSION_DENIED). In Firebase Console > Firestore Database > Rules, "
                    + "publish the Assignment 2 firebase.rules file. Then tap Try again.";
        }
        if (code == FirebaseFirestoreException.Code.UNAUTHENTICATED) {
            return "Your Firebase session is no longer valid. Log out and sign in again.";
        }
        if (code == FirebaseFirestoreException.Code.NOT_FOUND) {
            return "Create the (default) Cloud Firestore database in your ShopEase Firebase project.";
        }
        if (code == FirebaseFirestoreException.Code.UNAVAILABLE
                || code == FirebaseFirestoreException.Code.DEADLINE_EXCEEDED
                || code == FirebaseFirestoreException.Code.CANCELLED) {
            return "Unable to reach Firestore. Check your internet connection, then try again.";
        }
        if (code == FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED) {
            return "Firestore quota was exceeded. Check usage limits in Firebase Console.";
        }
        if (code == FirebaseFirestoreException.Code.FAILED_PRECONDITION) {
            return "Firestore cannot complete this operation. Check database configuration and indexes.";
        }
        return "Firestore could not complete this request"
                + (code == null ? "." : " (" + code.name() + ").")
                + " Check Firebase setup or Android Studio Logcat (ShopEaseFirestore).";
    }

    private static FirebaseFirestoreException.Code code(Exception error) {
        return error instanceof FirebaseFirestoreException
                ? ((FirebaseFirestoreException) error).getCode() : null;
    }
}

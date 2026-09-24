package android.text

interface Editable : CharSequence

/** Simple immutable Editable used by our EditText stand-in. */
class SimpleEditable(private val value: String) : Editable, CharSequence by value {
    override fun toString(): String = value
}

package android.graphics

import java.awt.geom.AffineTransform

class Matrix() {
    internal var transform = AffineTransform()

    constructor(src: Matrix?) : this() {
        if (src != null) transform = AffineTransform(src.transform)
    }

    fun isIdentity(): Boolean = transform.isIdentity

    fun reset() = transform.setToIdentity()

    fun setRotate(degrees: Float) = transform.setToRotation(Math.toRadians(degrees.toDouble()))

    fun setRotate(degrees: Float, px: Float, py: Float) =
        transform.setToRotation(Math.toRadians(degrees.toDouble()), px.toDouble(), py.toDouble())

    fun setScale(sx: Float, sy: Float) = transform.setToScale(sx.toDouble(), sy.toDouble())

    fun setTranslate(dx: Float, dy: Float) = transform.setToTranslation(dx.toDouble(), dy.toDouble())

    fun postRotate(degrees: Float): Boolean {
        transform.preConcatenate(AffineTransform.getRotateInstance(Math.toRadians(degrees.toDouble())))
        return true
    }

    fun postScale(sx: Float, sy: Float): Boolean {
        transform.preConcatenate(AffineTransform.getScaleInstance(sx.toDouble(), sy.toDouble()))
        return true
    }

    fun postTranslate(dx: Float, dy: Float): Boolean {
        transform.preConcatenate(AffineTransform.getTranslateInstance(dx.toDouble(), dy.toDouble()))
        return true
    }

    fun preRotate(degrees: Float): Boolean {
        transform.rotate(Math.toRadians(degrees.toDouble()))
        return true
    }

    fun preScale(sx: Float, sy: Float): Boolean {
        transform.scale(sx.toDouble(), sy.toDouble())
        return true
    }

    fun preTranslate(dx: Float, dy: Float): Boolean {
        transform.translate(dx.toDouble(), dy.toDouble())
        return true
    }
}

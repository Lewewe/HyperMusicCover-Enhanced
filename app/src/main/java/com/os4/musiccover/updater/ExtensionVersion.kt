package com.os4.musiccover.updater

/** Numeric release versions, with stable versions newer than their prereleases. */
internal object ExtensionVersion {
    fun isNewer(remote: String, local: String?): Boolean {
        if (local == null) return true
        fun parts(value: String) = value.removePrefix("v").substringBefore('+').split('-', limit = 2)
        val r = parts(remote)
        val l = parts(local)
        val rn = r[0].split('.').map { it.toIntOrNull() ?: return false }
        val ln = l[0].split('.').map { it.toIntOrNull() ?: return false }
        for (i in 0 until maxOf(rn.size, ln.size)) {
            val c = (rn.getOrElse(i) { 0 }).compareTo(ln.getOrElse(i) { 0 })
            if (c != 0) return c > 0
        }
        if (r.size != l.size) return r.size == 1
        if (r.size == 1) return false
        val rp = r[1].split('.')
        val lp = l[1].split('.')
        for (i in 0 until minOf(rp.size, lp.size)) {
            val a = rp[i].toIntOrNull()
            val b = lp[i].toIntOrNull()
            val c = when {
                a != null && b != null -> a.compareTo(b)
                a != null -> -1
                b != null -> 1
                else -> rp[i].compareTo(lp[i])
            }
            if (c != 0) return c > 0
        }
        return rp.size > lp.size
    }
}

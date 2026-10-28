package com.gathin.tvhome.feature.agent.ui

import android.graphics.Bitmap
import android.graphics.Color
import java.nio.charset.StandardCharsets

object AgentQr {

    @JvmStatic
    fun render(text: String, sizePixels: Int): Bitmap {
        val matrix = encode(text)
        val side = matrix.size
        val border = 2
        val totalSide = side + border * 2
        val pixels = IntArray(totalSide * totalSide) { Color.WHITE }

        for (r in 0 until side) {
            val rowPixels = (r + border) * totalSide + border
            for (c in 0 until side) {
                if (matrix[r][c]) {
                    pixels[rowPixels + c] = Color.BLACK
                }
            }
        }

        val baseBitmap = Bitmap.createBitmap(pixels, totalSide, totalSide, Bitmap.Config.ARGB_8888)
        val finalSize = maxOf(sizePixels, totalSide)
        return Bitmap.createScaledBitmap(baseBitmap, finalSize, finalSize, false)
    }

    internal fun encode(text: String): Array<BooleanArray> {
        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        val ver = selectVersion(bytes.size)
        val dataCodewords = encodePayload(bytes, ver.dataCodewords, ver.version)
        val allCodewords = generateCodewords(dataCodewords, ver)

        val side = 17 + ver.version * 4
        val modules = Array(side) { BooleanArray(side) }
        val isFunction = Array(side) { BooleanArray(side) }

        placeFunctionPatterns(modules, isFunction, ver)
        placeData(modules, isFunction, allCodewords)

        val bestMask = selectBestMask(modules, isFunction)
        applyMask(modules, isFunction, bestMask)
        placeFormatInfo(modules, bestMask)

        return modules
    }

    private data class VersionInfo(
        val version: Int,
        val dataCodewords: Int,
        val ecCodewordsPerBlock: Int,
        val blocks: List<Int>, // data length per block
        val alignments: IntArray
    )

    private val VERSIONS = arrayOf(
        VersionInfo(1, 16, 10, listOf(16), intArrayOf()),
        VersionInfo(2, 28, 16, listOf(28), intArrayOf(6, 18)),
        VersionInfo(3, 44, 26, listOf(44), intArrayOf(6, 22)),
        VersionInfo(4, 64, 18, listOf(32, 32), intArrayOf(6, 26)),
        VersionInfo(5, 86, 24, listOf(43, 43), intArrayOf(6, 30)),
        VersionInfo(6, 108, 16, listOf(27, 27, 27, 27), intArrayOf(6, 34)),
        VersionInfo(7, 124, 18, listOf(31, 31, 31, 31), intArrayOf(6, 22, 38)),
        VersionInfo(8, 154, 22, listOf(38, 38, 39, 39), intArrayOf(6, 24, 42)),
        VersionInfo(9, 182, 22, listOf(36, 36, 36, 37, 37), intArrayOf(6, 26, 46)),
        VersionInfo(10, 216, 26, listOf(43, 43, 43, 43, 44), intArrayOf(6, 28, 50))
    )

    private fun selectVersion(byteCount: Int): VersionInfo {
        for (v in VERSIONS) {
            val header = if (v.version < 10) 2 else 3
            if (byteCount + header <= v.dataCodewords) return v
        }
        return VERSIONS.last()
    }

    private fun encodePayload(bytes: ByteArray, capacity: Int, version: Int): ByteArray {
        val bitBuffer = BitBuffer()
        bitBuffer.append(4, 4)
        bitBuffer.append(bytes.size, if (version < 10) 8 else 16)
        for (b in bytes) {
            bitBuffer.append(b.toInt() and 0xFF, 8)
        }
        val remaining = capacity * 8 - bitBuffer.bitLength
        val term = minOf(4, remaining)
        if (term > 0) bitBuffer.append(0, term)
        while (bitBuffer.bitLength % 8 != 0) bitBuffer.append(0, 1)

        val padBytes = intArrayOf(0xEC, 0x11)
        var padIdx = 0
        while (bitBuffer.byteLength < capacity) {
            bitBuffer.append(padBytes[padIdx % 2], 8)
            padIdx++
        }
        return bitBuffer.toByteArray()
    }

    private fun generateCodewords(data: ByteArray, ver: VersionInfo): ByteArray {
        val dataBlocks = ArrayList<ByteArray>()
        var offset = 0
        for (len in ver.blocks) {
            val block = ByteArray(len)
            System.arraycopy(data, offset, block, 0, len)
            offset += len
            dataBlocks.add(block)
        }

        val ecBlocks = ArrayList<ByteArray>()
        for (block in dataBlocks) {
            ecBlocks.add(rsEncode(block, ver.ecCodewordsPerBlock))
        }

        val totalData = ver.dataCodewords
        val totalEc = ver.ecCodewordsPerBlock * ver.blocks.size
        val result = ByteArray(totalData + totalEc)
        var writeIdx = 0

        val maxDataLen = ver.blocks.maxOrNull() ?: 0
        for (i in 0 until maxDataLen) {
            for (block in dataBlocks) {
                if (i < block.size) result[writeIdx++] = block[i]
            }
        }

        for (i in 0 until ver.ecCodewordsPerBlock) {
            for (block in ecBlocks) {
                result[writeIdx++] = block[i]
            }
        }
        return result
    }

    private val EXP = IntArray(512)
    private val LOG = IntArray(256)

    init {
        var x = 1
        for (i in 0 until 255) {
            EXP[i] = x
            LOG[x] = i
            x = (x shl 1) xor (if (x >= 128) 0x11D else 0)
        }
        for (i in 255 until 512) EXP[i] = EXP[i - 255]
    }

    private fun gmul(a: Int, b: Int): Int =
        if (a == 0 || b == 0) 0 else EXP[LOG[a] + LOG[b]]

    private fun rsEncode(data: ByteArray, ecLen: Int): ByteArray {
        var gen = intArrayOf(1)
        for (i in 0 until ecLen) {
            val root = EXP[i]
            val next = IntArray(gen.size + 1)
            for (j in gen.indices) {
                next[j] = next[j] xor gen[j]
                next[j + 1] = next[j + 1] xor gmul(gen[j], root)
            }
            gen = next
        }

        val res = IntArray(data.size + ecLen)
        for (i in data.indices) res[i] = data[i].toInt() and 0xFF
        for (i in data.indices) {
            val lead = res[i]
            if (lead != 0) {
                for (j in gen.indices) res[i + j] = res[i + j] xor gmul(gen[j], lead)
            }
        }

        val ec = ByteArray(ecLen)
        for (i in 0 until ecLen) ec[i] = res[data.size + i].toByte()
        return ec
    }

    private fun placeFunctionPatterns(
        m: Array<BooleanArray>,
        f: Array<BooleanArray>,
        ver: VersionInfo
    ) {
        val side = m.size
        fun setMod(r: Int, c: Int, dark: Boolean) {
            m[r][c] = dark
            f[r][c] = true
        }

        fun placeFinder(top: Int, left: Int) {
            for (r in -1..7) {
                for (c in -1..7) {
                    val vr = top + r
                    val vc = left + c
                    if (vr in 0 until side && vc in 0 until side) {
                        val inFinder = r in 0..6 && c in 0..6
                        val dark = inFinder && (r == 0 || r == 6 || c == 0 || c == 6 || (r in 2..4 && c in 2..4))
                        setMod(vr, vc, dark)
                    }
                }
            }
        }

        placeFinder(0, 0)
        placeFinder(0, side - 7)
        placeFinder(side - 7, 0)

        for (i in 8 until side - 8) {
            setMod(6, i, i % 2 == 0)
            setMod(i, 6, i % 2 == 0)
        }

        val coords = ver.alignments
        val first = coords.firstOrNull()
        val last = coords.lastOrNull()
        for (r in coords) {
            for (c in coords) {
                val nearFinder = (r == first && c == first) || (r == first && c == last) ||
                    (r == last && c == first)
                if (nearFinder) continue
                for (dr in -2..2) {
                    for (dc in -2..2) {
                        val dark = (dr == -2 || dr == 2 || dc == -2 || dc == 2 || (dr == 0 && dc == 0))
                        setMod(r + dr, c + dc, dark)
                    }
                }
            }
        }

        setMod(4 * ver.version + 9, 8, true)
        placeVersionInfo(ver.version, side, ::setMod)

        for (i in 0..8) {
            f[8][i] = true
            f[i][8] = true
        }
        for (i in 0..7) {
            f[8][side - 1 - i] = true
            f[side - 1 - i][8] = true
        }
    }

    private fun placeVersionInfo(version: Int, side: Int, setMod: (Int, Int, Boolean) -> Unit) {
        if (version < 7) return
        var remainder = version
        repeat(12) { remainder = (remainder shl 1) xor ((remainder ushr 11) * 0x1F25) }
        val bits = (version shl 12) or remainder
        for (i in 0 until 18) {
            val dark = (bits ushr i) and 1 == 1
            val far = side - 11 + i % 3
            val near = i / 3
            setMod(near, far, dark)
            setMod(far, near, dark)
        }
    }

    private fun placeData(
        m: Array<BooleanArray>,
        f: Array<BooleanArray>,
        data: ByteArray
    ) {
        val side = m.size
        var bitIdx = 0
        val totalBits = data.size * 8
        var right = side - 1
        var goingUp = true

        while (right > 0) {
            if (right == 6) right--
            val range = if (goingUp) (side - 1 downTo 0) else (0 until side)
            for (row in range) {
                for (step in 0..1) {
                    val col = right - step
                    if (!f[row][col]) {
                        val dark = if (bitIdx < totalBits) {
                            val byteVal = data[bitIdx / 8].toInt() and 0xFF
                            val bit = (byteVal ushr (7 - (bitIdx % 8))) and 1
                            bit == 1
                        } else false
                        m[row][col] = dark
                        bitIdx++
                    }
                }
            }
            goingUp = !goingUp
            right -= 2
        }
    }

    private fun maskCondition(mask: Int, r: Int, c: Int): Boolean = when (mask) {
        0 -> (r + c) % 2 == 0
        1 -> r % 2 == 0
        2 -> c % 3 == 0
        3 -> (r + c) % 3 == 0
        4 -> (r / 2 + c / 3) % 2 == 0
        5 -> ((r * c) % 2 + (r * c) % 3) == 0
        6 -> (((r * c) % 2 + (r * c) % 3) % 2) == 0
        else -> (((r + c) % 2 + (r * c) % 3) % 2) == 0
    }

    private fun applyMask(m: Array<BooleanArray>, f: Array<BooleanArray>, mask: Int) {
        val side = m.size
        for (r in 0 until side) {
            for (c in 0 until side) {
                if (!f[r][c] && maskCondition(mask, r, c)) {
                    m[r][c] = !m[r][c]
                }
            }
        }
    }

    private fun selectBestMask(m: Array<BooleanArray>, f: Array<BooleanArray>): Int {
        var bestMask = 0
        var minPenalty = Int.MAX_VALUE
        val side = m.size
        for (mask in 0..7) {
            applyMask(m, f, mask)
            var penalty = 0
            for (r in 0 until side) {
                var runColor = false
                var runLen = 0
                for (c in 0 until side) {
                    if (c == 0 || m[r][c] == runColor) {
                        runLen++
                    } else {
                        if (runLen >= 5) penalty += 3 + (runLen - 5)
                        runColor = m[r][c]
                        runLen = 1
                    }
                }
                if (runLen >= 5) penalty += 3 + (runLen - 5)
            }
            applyMask(m, f, mask)
            if (penalty < minPenalty) {
                minPenalty = penalty
                bestMask = mask
            }
        }
        return bestMask
    }

    private fun placeFormatInfo(m: Array<BooleanArray>, mask: Int) {
        // EC Level M = 00 -> format data = 00_mask
        val formatData = (0 shl 3) or mask
        var bch = formatData shl 10
        val generator = 0x537
        for (i in 4 downTo 0) {
            if ((bch and (1 shl (i + 10))) != 0) {
                bch = bch xor (generator shl i)
            }
        }
        val format = ((formatData shl 10) or bch) xor 0x5412
        val side = m.size

        val bits = BooleanArray(15) { (format and (1 shl it)) != 0 }

        for (i in 0..5) m[i][8] = bits[i]
        m[7][8] = bits[6]
        m[8][8] = bits[7]
        m[8][7] = bits[8]
        for (i in 9..14) m[8][14 - i] = bits[i]

        for (i in 0..7) m[8][side - 1 - i] = bits[i]
        for (i in 0..6) m[side - 7 + i][8] = bits[8 + i]
    }

    private class BitBuffer {
        private var buffer = ByteArray(64)
        var bitLength = 0
            private set
        val byteLength get() = (bitLength + 7) / 8

        fun append(value: Int, numBits: Int) {
            for (i in numBits - 1 downTo 0) {
                val bit = (value ushr i) and 1
                if (bitLength / 8 >= buffer.size) {
                    val next = ByteArray(buffer.size * 2)
                    System.arraycopy(buffer, 0, next, 0, buffer.size)
                    buffer = next
                }
                if (bit != 0) {
                    buffer[bitLength / 8] = (buffer[bitLength / 8].toInt() or (1 shl (7 - (bitLength % 8)))).toByte()
                }
                bitLength++
            }
        }

        fun toByteArray(): ByteArray {
            val result = ByteArray(byteLength)
            System.arraycopy(buffer, 0, result, 0, byteLength)
            return result
        }
    }
}

// Copyright 2017 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
////////////////////////////////////////////////////////////////////////////////
package app.cash.zipline.loader.internal.tink.subtle

/**
 * Defines field 25519 function based on [curve25519-donna C
 * implementation](https://github.com/agl/curve25519-donna/blob/master/curve25519-donna.c)
 * (mostly identical).
 *
 * Field elements are written as an array of signed, 64-bit limbs (an array of longs), least
 * significant first. The value of the field element is:
 *
 * ```
 * x[0] + 2^26·x[1] + 2^51·x[2] + 2^77·x[3] + 2^102·x[4] + 2^128·x[5] + 2^153·x[6] + 2^179·x[7] +
 * 2^204·x[8] + 2^230·x[9],
 * ```
 *
 * i.e. the limbs are 26, 25, 26, 25, ... bits wide.
 */
internal object Field25519 {
  /**
   * During Field25519 computation, the mixed radix representation may be in different forms:
   *
   *  *  Reduced-size form: the array has size at most 10.
   *  *  Non-reduced-size form: the array is not reduced modulo 2^255 - 19 and has size at most
   * 19.
   *
   *
   * TODO(quannguyen):
   *
   *  *  Clarify ill-defined terminologies.
   *  *  The reduction procedure is different from DJB's paper
   * (http://cr.yp.to/ecdh/curve25519-20060209.pdf). The coefficients after reducing degree and
   * reducing coefficients aren't guaranteed to be in range {-2^25, ..., 2^25}. We should check to
   * see what's going on.
   *  *  Consider using method mult() everywhere and making product() private.
   *
   */
  const val FIELD_LEN = 32
  const val LIMB_CNT = 10
  private const val TWO_TO_25 = (1 shl 25).toLong()
  private const val TWO_TO_26 = TWO_TO_25 shl 1

  private val EXPAND_START = intArrayOf(0, 3, 6, 9, 12, 16, 19, 22, 25, 28)
  private val EXPAND_SHIFT = intArrayOf(0, 2, 3, 5, 6, 0, 1, 3, 4, 6)
  private val MASK = intArrayOf(0x3ffffff, 0x1ffffff)
  private val SHIFT = intArrayOf(26, 25)

  /**
   * Sums two numbers: output = in1 + in2
   *
   * On entry: in1, in2 are in reduced-size form.
   */
  fun sum(output: LongArray, in1: LongArray, in2: LongArray) {
    for (i in 0 until LIMB_CNT) {
      output[i] = in1[i] + in2[i]
    }
  }

  /**
   * Sums two numbers: output += in
   *
   * On entry: in is in reduced-size form.
   */
  fun sum(output: LongArray, in1: LongArray) {
    sum(output, output, in1)
  }

  /**
   * Find the difference of two numbers: output = in1 - in2
   * (note the order of the arguments!).
   *
   * On entry: in1, in2 are in reduced-size form.
   */
  fun sub(output: LongArray, in1: LongArray, in2: LongArray) {
    for (i in 0 until LIMB_CNT) {
      output[i] = in1[i] - in2[i]
    }
  }

  /**
   * Multiply a number by a scalar: output = in * scalar
   */
  fun scalarProduct(output: LongArray, inLongArray: LongArray, scalar: Long) {
    for (i in 0 until LIMB_CNT) {
      output[i] = inLongArray[i] * scalar
    }
  }

  /**
   * Multiply two numbers: out = in2 * in
   *
   * output must be distinct to both inputs. The inputs are reduced coefficient form,
   * the output is not.
   *
   * out[x] <= 14 * the largest product of the input limbs.
   */
  fun product(out: LongArray, in2: LongArray, in1: LongArray) {
    out[0] = (in2[0] * in1[0])
    out[1] = (
      (
        in2[0] * in1[1] +
      in2[1] * in1[0]
      )
    )
    out[2] = (
      2 * in2[1] * in1[1] +
      in2[0] * in1[2] +
      in2[2] * in1[0]
    )
    out[3] = (
      in2[1] * in1[2] +
      in2[2] * in1[1] +
      in2[0] * in1[3] +
      in2[3] * in1[0]
    )
    out[4] = (
      in2[2] * in1[2] +
      2 * (in2[1] * in1[3] + in2[3] * in1[1]) +
      in2[0] * in1[4] +
      in2[4] * in1[0]
    )
    out[5] = (
      in2[2] * in1[3] +
      in2[3] * in1[2] +
      in2[1] * in1[4] +
      in2[4] * in1[1] +
      in2[0] * in1[5] +
      in2[5] * in1[0]
    )
    out[6] = (
      2 * (in2[3] * in1[3] + in2[1] * in1[5] + in2[5] * in1[1]) +
      in2[2] * in1[4] +
      in2[4] * in1[2] +
      in2[0] * in1[6] +
      in2[6] * in1[0]
    )
    out[7] = (
      in2[3] * in1[4] +
      in2[4] * in1[3] +
      in2[2] * in1[5] +
      in2[5] * in1[2] +
      in2[1] * in1[6] +
      in2[6] * in1[1] +
      in2[0] * in1[7] +
      in2[7] * in1[0]
    )
    out[8] = (
      in2[4] * in1[4] +
      2 * (in2[3] * in1[5] + in2[5] * in1[3] + in2[1] * in1[7] + in2[7] * in1[1]) +
      in2[2] * in1[6] +
      in2[6] * in1[2] +
      in2[0] * in1[8] +
      in2[8] * in1[0]
    )
    out[9] = (
      in2[4] * in1[5] +
      in2[5] * in1[4] +
      in2[3] * in1[6] +
      in2[6] * in1[3] +
      in2[2] * in1[7] +
      in2[7] * in1[2] +
      in2[1] * in1[8] +
      in2[8] * in1[1] +
      in2[0] * in1[9] +
      in2[9] * in1[0]
    )
    out[10] = (
      2 * (in2[5] * in1[5] + in2[3] * in1[7] + in2[7] * in1[3] + in2[1] * in1[9] + in2[9] * in1[1]) +
        in2[4] * in1[6] +
        in2[6] * in1[4] +
        in2[2] * in1[8] +
        in2[8] * in1[2]
    )
    out[11] = (
      in2[5] * in1[6] +
      in2[6] * in1[5] +
      in2[4] * in1[7] +
      in2[7] * in1[4] +
      in2[3] * in1[8] +
      in2[8] * in1[3] +
      in2[2] * in1[9] +
      in2[9] * in1[2]
    )
    out[12] = (
      in2[6] * in1[6] +
      2 * (in2[5] * in1[7] + in2[7] * in1[5] + in2[3] * in1[9] + in2[9] * in1[3]) +
      in2[4] * in1[8] +
      in2[8] * in1[4]
    )
    out[13] = (
      in2[6] * in1[7] +
      in2[7] * in1[6] +
      in2[5] * in1[8] +
      in2[8] * in1[5] +
      in2[4] * in1[9] +
      in2[9] * in1[4]
    )
    out[14] = (
      2 * (in2[7] * in1[7] + in2[5] * in1[9] + in2[9] * in1[5]) +
      in2[6] * in1[8] +
      in2[8] * in1[6]
    )
    out[15] = (
      in2[7] * in1[8] +
      in2[8] * in1[7] +
      in2[6] * in1[9] +
      in2[9] * in1[6]
    )
    out[16] = (
      (
        in2[8] * in1[8] +
      2 * (in2[7] * in1[9] + in2[9] * in1[7])
      )
    )
    out[17] = (
      (
        in2[8] * in1[9] +
      in2[9] * in1[8]
      )
    )
    out[18] = (2 * in2[9] * in1[9])
  }

  /**
   * Reduce a field element by calling reduceSizeByModularReduction and reduceCoefficients.
   *
   * @param input An input array of any length. If the array has 19 elements, it will be used as
   *     temporary buffer and its contents changed.
   * @param output An output array of size [LIMB_CNT]. After the call `|output[i]| < 2^26` will
   *     hold.
   */
  fun reduce(input: LongArray, output: LongArray) {
    val tmp: LongArray
    if (input.size == 19) {
      tmp = input
    } else {
      tmp = LongArray(19)
      input.copyInto(tmp, endIndex = input.size)
    }
    reduceSizeByModularReduction(tmp)
    reduceCoefficients(tmp)
    tmp.copyInto(output, endIndex = LIMB_CNT)
  }

  /**
   * Reduce a long form to a reduced-size form by taking the input mod 2^255 - 19.
   *
   * On entry: `|output[i]| < 14*2^54`
   * On exit: `|output[0..8]| < 280*2^54`
   */
  fun reduceSizeByModularReduction(output: LongArray) {
    // The coefficients x[10], x[11],..., x[18] are eliminated by reduction modulo 2^255 - 19.
    // For example, the coefficient x[18] is multiplied by 19 and added to the coefficient x[8].
    //
    // Each of these shifts and adds ends up multiplying the value by 19.
    //
    // For output[0..8], the absolute entry value is < 14*2^54 and we add, at most, 19*14*2^54 thus,
    // on exit, |output[0..8]| < 280*2^54.
    output[8] += output[18] shl 4
    output[8] += output[18] shl 1
    output[8] += output[18]
    output[7] += output[17] shl 4
    output[7] += output[17] shl 1
    output[7] += output[17]
    output[6] += output[16] shl 4
    output[6] += output[16] shl 1
    output[6] += output[16]
    output[5] += output[15] shl 4
    output[5] += output[15] shl 1
    output[5] += output[15]
    output[4] += output[14] shl 4
    output[4] += output[14] shl 1
    output[4] += output[14]
    output[3] += output[13] shl 4
    output[3] += output[13] shl 1
    output[3] += output[13]
    output[2] += output[12] shl 4
    output[2] += output[12] shl 1
    output[2] += output[12]
    output[1] += output[11] shl 4
    output[1] += output[11] shl 1
    output[1] += output[11]
    output[0] += output[10] shl 4
    output[0] += output[10] shl 1
    output[0] += output[10]
  }

  /**
   * Reduce all coefficients of the short form input so that |x| < 2^26.
   *
   * On entry: `|output[i]| < 280*2^54`
   */
  fun reduceCoefficients(output: LongArray) {
    output[10] = 0
    var i = 0
    while (i < LIMB_CNT) {
      var over = output[i] / TWO_TO_26
      // The entry condition (that |output[i]| < 280*2^54) means that over is, at most, 280*2^28 in
      // the first iteration of this loop. This is added to the next limb and we can approximate the
      // resulting bound of that limb by 281*2^54.
      output[i] -= over shl 26
      output[i + 1] += over

      // For the first iteration, |output[i+1]| < 281*2^54, thus |over| < 281*2^29. When this is
      // added to the next limb, the resulting bound can be approximated as 281*2^54.
      //
      // For subsequent iterations of the loop, 281*2^54 remains a conservative bound and no
      // overflow occurs.
      over = output[i + 1] / TWO_TO_25
      output[i + 1] -= over shl 25
      output[i + 2] += over
      i += 2
    }
    // Now |output[10]| < 281*2^29 and all other coefficients are reduced.
    output[0] += output[10] shl 4
    output[0] += output[10] shl 1
    output[0] += output[10]

    output[10] = 0
    // Now output[1..9] are reduced, and |output[0]| < 2^26 + 19*281*2^29 so |over| will be no more
    // than 2^16.
    val over = output[0] / TWO_TO_26
    output[0] -= over shl 26
    output[1] += over
    // Now output[0,2..9] are reduced, and |output[1]| < 2^25 + 2^16 < 2^26. The bound on
    // |output[1]| is sufficient to meet our needs.
  }

  /**
   * A helpful wrapper around {@ref Field25519#product}: output = in * in2.
   *
   * On entry: `|in[i]| < 2^27 and |in2[i]| < 2^27.`
   *
   * The output is reduced degree (indeed, one need only provide storage for 10 limbs) and
   * `|output[i]| < 2^26`.
   */
  fun mult(output: LongArray, inLongArray: LongArray, in2: LongArray) {
    val t = LongArray(19)
    product(t, inLongArray, in2)
    // |t[i]| < 2^26
    reduce(t, output)
  }

  /**
   * Square a number: out = in**2
   *
   * output must be distinct from the input. The inputs are reduced coefficient form, the output is
   * not.
   *
   * `out[x] <= 14 * the largest product of the input limbs.`
   */
  private fun squareInner(out: LongArray, in1: LongArray) {
    out[0] = (in1[0] * in1[0])
    out[1] = (2 * in1[0] * in1[1])
    out[2] = (2 * (in1[1] * in1[1] + in1[0] * in1[2]))
    out[3] = (2 * (in1[1] * in1[2] + in1[0] * in1[3]))
    out[4] = (
      in1[2] * in1[2] +
      4 * in1[1] * in1[3] +
      2 * in1[0] * in1[4]
    )
    out[5] = (2 * (in1[2] * in1[3] + in1[1] * in1[4] + in1[0] * in1[5]))
    out[6] = (2 * (in1[3] * in1[3] + in1[2] * in1[4] + in1[0] * in1[6] + 2 * in1[1] * in1[5]))
    out[7] = (2 * (in1[3] * in1[4] + in1[2] * in1[5] + in1[1] * in1[6] + in1[0] * in1[7]))
    out[8] = (
      (
        in1[4] * in1[4] +
      2 * (in1[2] * in1[6] + in1[0] * in1[8] + 2 * (in1[1] * in1[7] + in1[3] * in1[5]))
      )
    )
    out[9] = (2 * (in1[4] * in1[5] + in1[3] * in1[6] + in1[2] * in1[7] + in1[1] * in1[8] + in1[0] * in1[9]))
    out[10] = (
      2 * (
        in1[5] * in1[5] +
      in1[4] * in1[6] +
      in1[2] * in1[8] +
      2 * (in1[3] * in1[7] + in1[1] * in1[9])
      )
    )
    out[11] = (2 * (in1[5] * in1[6] + in1[4] * in1[7] + in1[3] * in1[8] + in1[2] * in1[9]))
    out[12] = (
      (
        in1[6] * in1[6] +
      2 * (in1[4] * in1[8] + 2 * (in1[5] * in1[7] + in1[3] * in1[9]))
      )
    )
    out[13] = (2 * (in1[6] * in1[7] + in1[5] * in1[8] + in1[4] * in1[9]))
    out[14] = (2 * (in1[7] * in1[7] + in1[6] * in1[8] + 2 * in1[5] * in1[9]))
    out[15] = (2 * (in1[7] * in1[8] + in1[6] * in1[9]))
    out[16] = (in1[8] * in1[8] + 4 * in1[7] * in1[9])
    out[17] = (2 * in1[8] * in1[9])
    out[18] = (2 * in1[9] * in1[9])
  }

  /**
   * Returns in^2.
   *
   * On entry: The |in| argument is in reduced coefficients form and `|in[i]| < 2^27`.
   *
   * On exit: The |output| argument is in reduced coefficients form (indeed, one need only provide
   * storage for 10 limbs) and `|out[i]| < 2^26`.
   */
  fun square(output: LongArray, in1: LongArray) {
    val t = LongArray(19)
    squareInner(t, in1)
    // |t[i]| < 14*2^54 because the largest product of two limbs will be < 2^(27+27) and SquareInner
    // adds together, at most, 14 of those products.
    reduce(t, output)
  }

  /**
   * Takes a little-endian, 32-byte number and expands it into mixed radix form.
   */
  fun expand(input: ByteArray): LongArray {
    val output = LongArray(LIMB_CNT)
    for (i in 0 until LIMB_CNT) {
      output[i] = (
        (input[EXPAND_START[i]].toInt() and 0xff).toLong()
        or ((input[EXPAND_START[i] + 1].toInt() and 0xff).toLong() shl 8)
        or ((input[EXPAND_START[i] + 2].toInt() and 0xff).toLong() shl 16)
        or ((input[EXPAND_START[i] + 3].toInt() and 0xff).toLong() shl 24)
      ) shr EXPAND_SHIFT[i] and MASK[i and 1].toLong()
    }
    return output
  }

  /**
   * Takes a fully reduced mixed radix form number and contract it into a little-endian, 32-byte
   * array.
   *
   * On entry: `|input_limbs[i]| < 2^26`
   */
  fun contract(inputLimbs: LongArray): ByteArray {
    val input = inputLimbs.copyOf(LIMB_CNT)
    for (j in 0..1) {
      for (i in 0..8) {
        // This calculation is a time-invariant way to make input[i] non-negative by borrowing
        // from the next-larger limb.
        val carry = -(input[i] and (input[i] shr 31) shr SHIFT[i and 1]).toInt()
        input[i] = input[i] + (carry shl SHIFT[i and 1])
        input[i + 1] -= carry.toLong()
      }

      // There's no greater limb for input[9] to borrow from, but we can multiply by 19 and borrow
      // from input[0], which is valid mod 2^255-19.
      run {
        val carry = -(input[9] and (input[9] shr 31) shr 25).toInt()
        input[9] += (carry shl 25).toLong()
        input[0] -= (carry * 19).toLong()
      }

      // After the first iteration, input[1..9] are non-negative and fit within 25 or 26 bits,
      // depending on position. However, input[0] may be negative.
    }

    // The first borrow-propagation pass above ended with every limb except (possibly) input[0]
    // non-negative.
    //
    // If input[0] was negative after the first pass, then it was because of a carry from input[9].
    // On entry, input[9] < 2^26 so the carry was, at most, one, since (2**26-1) >> 25 = 1. Thus
    // input[0] >= -19.
    //
    // In the second pass, each limb is decreased by at most one. Thus the second borrow-propagation
    // pass could only have wrapped around to decrease input[0] again if the first pass left
    // input[0] negative *and* input[1] through input[9] were all zero.  In that case, input[1] is
    // now 2^25 - 1, and this last borrow-propagation step will leave input[1] non-negative.
    run {
      val carry = -(input[0] and (input[0] shr 31) shr 26).toInt()
      input[0] += (carry shl 26).toLong()
      input[1] -= carry.toLong()
    }

    // All input[i] are now non-negative. However, there might be values between 2^25 and 2^26 in a
    // limb which is, nominally, 25 bits wide.
    for (j in 0..1) {
      for (i in 0..8) {
        val carry = (input[i] shr SHIFT[i and 1]).toInt()
        input[i] = input[i] and MASK[i and 1].toLong()
        input[i + 1] += carry.toLong()
      }
    }
    run {
      val carry = (input[9] shr 25).toInt()
      input[9] = input[9] and 0x1ffffffL
      input[0] += (19 * carry).toLong()
    }

    // If the first carry-chain pass, just above, ended up with a carry from input[9], and that
    // caused input[0] to be out-of-bounds, then input[0] was < 2^26 + 2*19, because the carry was,
    // at most, two.
    //
    // If the second pass carried from input[9] again then input[0] is < 2*19 and the input[9] ->
    // input[0] carry didn't push input[0] out of bounds.

    // It still remains the case that input might be between 2^255-19 and 2^255. In this case,
    // input[1..9] must take their maximum value and input[0] must be >= (2^255-19) & 0x3ffffff,
    // which is 0x3ffffed.
    var mask = gte(input[0].toInt(), 0x3ffffed)
    for (i in 1 until LIMB_CNT) {
      mask = mask and eq(input[i].toInt(), MASK[i and 1])
    }

    // mask is either 0xffffffff (if input >= 2^255-19) and zero otherwise. Thus this conditionally
    // subtracts 2^255-19.
    input[0] -= (mask and 0x3ffffed).toLong()
    input[1] -= (mask and 0x1ffffff).toLong()
    run {
      var i = 2
      while (i < LIMB_CNT) {
        input[i] -= (mask and 0x3ffffff).toLong()
        input[i + 1] -= (mask and 0x1ffffff).toLong()
        i += 2
      }
    }

    for (i in 0 until LIMB_CNT) {
      input[i] = input[i] shl EXPAND_SHIFT[i]
    }
    val output = ByteArray(FIELD_LEN)
    for (i in 0 until LIMB_CNT) {
      output[EXPAND_START[i]] = (
        output[EXPAND_START[i]]
        .toLong() or (input[i] and 0xffL)
      ).toByte()
      output[EXPAND_START[i] + 1] = (
        output[EXPAND_START[i] + 1]
        .toLong() or (input[i] shr 8 and 0xffL)
      ).toByte()
      output[EXPAND_START[i] + 2] = (
        output[EXPAND_START[i] + 2]
        .toLong() or (input[i] shr 16 and 0xffL)
      ).toByte()
      output[EXPAND_START[i] + 3] = (
        output[EXPAND_START[i] + 3]
        .toLong() or (input[i] shr 24 and 0xffL)
      ).toByte()
    }
    return output
  }

  /**
   * Computes inverse of z = z(2^255 - 21)
   *
   * Shamelessly copied from agl's code which was shamelessly copied from djb's code. Only the
   * comment format and the variable namings are different from those.
   */
  fun inverse(out: LongArray, z: LongArray) {
    val z2 = LongArray(LIMB_CNT)
    val z9 = LongArray(LIMB_CNT)
    val z11 = LongArray(LIMB_CNT)
    val z2To5Minus1 = LongArray(LIMB_CNT)
    val z2To10Minus1 = LongArray(LIMB_CNT)
    val z2To20Minus1 = LongArray(LIMB_CNT)
    val z2To50Minus1 = LongArray(LIMB_CNT)
    val z2To100Minus1 = LongArray(LIMB_CNT)
    val t0 = LongArray(LIMB_CNT)
    val t1 = LongArray(LIMB_CNT)

    square(z2, z) // 2
    square(t1, z2) // 4
    square(t0, t1) // 8
    mult(z9, t0, z) // 9
    mult(z11, z9, z2) // 11
    square(t0, z11) // 22
    mult(z2To5Minus1, t0, z9) // 2^5 - 2^0 = 31

    square(t0, z2To5Minus1) // 2^6 - 2^1
    square(t1, t0) // 2^7 - 2^2
    square(t0, t1) // 2^8 - 2^3
    square(t1, t0) // 2^9 - 2^4
    square(t0, t1) // 2^10 - 2^5
    mult(z2To10Minus1, t0, z2To5Minus1) // 2^10 - 2^0

    square(t0, z2To10Minus1) // 2^11 - 2^1
    square(t1, t0) // 2^12 - 2^2
    run {
      var i = 2
      while (i < 10) { // 2^20 - 2^10
        square(t0, t1)
        square(t1, t0)
        i += 2
      }
    }
    mult(z2To20Minus1, t1, z2To10Minus1) // 2^20 - 2^0

    square(t0, z2To20Minus1) // 2^21 - 2^1
    square(t1, t0) // 2^22 - 2^2
    run {
      var i = 2
      while (i < 20) { // 2^40 - 2^20
        square(t0, t1)
        square(t1, t0)
        i += 2
      }
    }
    mult(t0, t1, z2To20Minus1) // 2^40 - 2^0

    square(t1, t0) // 2^41 - 2^1
    square(t0, t1) // 2^42 - 2^2
    run {
      var i = 2
      while (i < 10) { // 2^50 - 2^10
        square(t1, t0)
        square(t0, t1)
        i += 2
      }
    }
    mult(z2To50Minus1, t0, z2To10Minus1) // 2^50 - 2^0

    square(t0, z2To50Minus1) // 2^51 - 2^1
    square(t1, t0) // 2^52 - 2^2
    run {
      var i = 2
      while (i < 50) { // 2^100 - 2^50
        square(t0, t1)
        square(t1, t0)
        i += 2
      }
    }
    mult(z2To100Minus1, t1, z2To50Minus1) // 2^100 - 2^0

    square(t1, z2To100Minus1) // 2^101 - 2^1
    square(t0, t1) // 2^102 - 2^2
    run {
      var i = 2
      while (i < 100) { // 2^200 - 2^100
        square(t1, t0)
        square(t0, t1)
        i += 2
      }
    }
    mult(t1, t0, z2To100Minus1) // 2^200 - 2^0

    square(t0, t1) // 2^201 - 2^1
    square(t1, t0) // 2^202 - 2^2
    var i = 2
    while (i < 50) { // 2^250 - 2^50
      square(t0, t1)
      square(t1, t0)
      i += 2
    }
    mult(t0, t1, z2To50Minus1) // 2^250 - 2^0

    square(t1, t0) // 2^251 - 2^1
    square(t0, t1) // 2^252 - 2^2
    square(t1, t0) // 2^253 - 2^3
    square(t0, t1) // 2^254 - 2^4
    square(t1, t0) // 2^255 - 2^5
    mult(out, t1, z11) // 2^255 - 21
  }

  /**
   * Returns 0xffffffff iff a == b and zero otherwise.
   */
  private fun eq(a: Int, b: Int): Int {
    var a = a
    a = (a xor b).inv()
    a = a and (a shl 16)
    a = a and (a shl 8)
    a = a and (a shl 4)
    a = a and (a shl 2)
    a = a and (a shl 1)
    return a shr 31
  }

  /**
   * returns 0xffffffff if a >= b and zero otherwise, where a and b are both non-negative.
   */
  private fun gte(a: Int, b: Int): Int {
    var a = a
    a -= b
    // a >= 0 iff a >= b.
    return (a shr 31).inv()
  }
}

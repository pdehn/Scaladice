package com.pdehaan.scaladice

import java.util.concurrent.ThreadLocalRandom

import scala.annotation.tailrec

case class Distribution[A](weightedValues: Array[(A, Double)])
{
    def cumulativeWeightedValues(implicit ord: Ordering[A] = null) = {
        def f(cp: (A, Double), wv: (A, Double)) = wv._1 -> (cp._2 + wv._2)
        (if (ord == null) weightedValues else weightedValues.sortBy(_._1)(ord))
            .scanLeft(null.asInstanceOf[A] -> 0.0)(f)
            .drop(1)
    }

    def inverseCumulativeWeightedValues(implicit ord: Ordering[A] = null) =
        cumulativeWeightedValues map { case (v, p) => v -> (1.0 - p) }

    /**
     * Normalizes weights to sum to 1.0
     * @return
     */
    private def normalize = {
        val wt = weightedValues map { _._2 } sum
        val xs = weightedValues map {
            case (v, w) => v -> w / wt
        }
        Distribution(xs)
    }

    /**
     * Collapses all weighted value pairs that have the same value, summing the
     * weights for each group of pairs with the same value
     * @return
     */
    private def collapse = {
        val xs = weightedValues groupBy { _._1 } map {
            case (v, wvs) => v -> (wvs map { _._2 } sum)
        }
        Distribution(xs.toArray)
    }

    /**
     * Applies f to all values in the distribution.
     */
    final def map[B](f: A => B) = {
        // apply f to all values, weights unchanged
        val xs = weightedValues map { case (v, w) => f(v) -> w }

        // f can be non-injective, so combine pairs with the same value
        Distribution(xs).collapse
    }

    /**
     * Applies f to all values in the distribution, then merges the resulting
     * distribution of distributions.
     */
    final def flatMap[B](f: A => Distribution[B]) = {
        val xs = for (
            (v1, w1) <- weightedValues;
            (v2, w2) <- f(v1).weightedValues) yield v2 -> w1 * w2
        Distribution(xs).collapse
    }

    /**
     * Returns a new distribution containing only values for which `f` evaluates
     * to true
     */
    final def filter[B](pred: A => Boolean) =
    {
        val xs = weightedValues filter { case (v, _) => pred(v) }
        Distribution(xs).normalize
    }

    @tailrec
    final def markov(f: A => Distribution[A])(n: Int): Distribution[A] =
    {
        if (n == 0) this
        else flatMap(f).markov(f)(n-1)
    }

    /**
     * Create a new distribution that represents the result of sampling this
     * distribution n times and combining the results, maintaining sample order.
     */
    final def repeat(n: Int): Distribution[Seq[A]] =
    {
        def append(sas: Seq[A]): Distribution[Seq[A]] =
            map(sas :+ _)

        @tailrec
        def f(d: Distribution[Seq[A]], n: Int): Distribution[Seq[A]] =
            if (n == 1) d
            else f(d.flatMap(append), n - 1)

        f(map(Seq(_)), n)
    }

    /**
     * Create a new distribution that represents the result of sampling this
     * distribution n times and combining the results, ignoring sample order.
     */
    final def repeatUnordered(n: Int)(implicit ord: Ordering[A]) =
    {
        def append(sas: Map[A, Int]): Distribution[Map[A, Int]] =
            map({ case v => sas.updated(v, sas.getOrElse(v, 0) + 1)})

        @tailrec
        def f(d: Distribution[Map[A, Int]], n: Int): Distribution[Map[A, Int]] =
            if (n == 1) d
            else f(d.flatMap(append), n - 1)

        f(map(x => Map(x -> 1)), n).map {
            case m => m.toSeq.flatMap(x => (1 to x._2).map(_ => x._1).sorted)
        } sorted
    }

    /**
     * "Rerolls" values in this distribution for which the predicate returns
     * true.
     */
    def rerollWhere(pred: A => Boolean) =
        flatMap(v => {
            if (pred(v)) this
            else Distribution.fixed(v)
        })

    /**
     * Combines this distribution with another, the output represents all
     * possible combinations of both distributions' values.
     */
    def zip[B](d: Distribution[B]): Distribution[(A, B)] =
    {
        val xs = for (
            (v1, w1) <- weightedValues;
            (v2, w2) <- d.weightedValues) yield (v1, v2) -> w1 * w2
        Distribution(xs).collapse
    }

    def zipWith[B, C](d: Distribution[B])(f: (A, B) => C) =
        zip(d).map({
            case (a, b) => f(a, b)
        })

    /**
     * Computes distribution resulting from adding every possible pair of values
     * from two distributions.
     */
    def +(d: Distribution[A])(implicit n: Numeric[A]) =
    {
        val xs = for (
            (v1, w1) <- weightedValues;
            (v2, w2) <- d.weightedValues) yield n.plus(v1, v2) -> w1 * w2
        Distribution(xs).collapse
    }

    /**
     * Adds `x` to all values in this distribution
     */
    def +(x: A)(implicit n: Numeric[A]) = map(n.plus(x, _))

    def -(d: Distribution[A])(implicit n: Numeric[A]) =
    {
        val xs = for (
            (v1, w1) <- weightedValues;
            (v2, w2) <- d.weightedValues) yield n.minus(v1, v2) -> w1 * w2
        Distribution(xs).collapse
    }

    def -(x: A)(implicit n: Numeric[A]) = map(n.minus(x, _))

    /**
     * Computes expected value (mean) of this distribution.
     */
    def ev(implicit n: Numeric[A]) =
        weightedValues map {
            case (v, w) => n.toDouble(v) * w
        } sum

    def pr(pred: A => Boolean): Double =
    {
        weightedValues filter(x => pred(x._1)) map(_._2) sum
    }

    /**
     * Return a random value from this distribution.
     */
    def sample() =
    {
        val rand = ThreadLocalRandom.current()
        val x = rand.nextDouble()
        cumulativeWeightedValues.find(_._2 >= x) match {
            case Some((v, _)) => v
            case None => cumulativeWeightedValues.last._1
        }
    }

    /**
     * Return a sequence of `n` random values from this distribution.
     */
    def sample(n: Int): Seq[A] = (1 to n) map { i => sample() }

    /**
     * Compute a new distribution by taking `n` random samples from this
     * distribution.
     */
    def resample(n: Int) =
    {
        val wvs = sample(n) groupBy identity map {
            case (v, vs) => v -> vs.length.toDouble
        }
        Distribution(wvs.toArray).normalize
    }

    private def hist(ord: Ordering[A], pvalues: Array[(A, Double)]) =
    {
        val scale = 100
        val maxWidth = weightedValues map(_._1.toString.length) max
        val fmt = s"%${maxWidth}s %5.2f%% %s"
        val wvs = if (ord != null) pvalues.sortBy(_._1)(ord) else pvalues
        val lines = for (
            (a, p) <- wvs;
            hashes = (p * scale).toInt
        ) yield fmt.format(a.toString, p*100, "#" * hashes)
        lines.mkString("\n")
    }

    def hist(implicit ord: Ordering[A] = null): String =
        hist(ord, weightedValues)

    def cumulativeHist(implicit ord: Ordering[A]): String =
        hist(ord, cumulativeWeightedValues)

    def inverseCumulativeHist(implicit ord: Ordering[A]): String =
        hist(ord, inverseCumulativeWeightedValues)
}

object Distribution {

    /**
     * Return a distribution for an n-sided die.
     */
    def d(n: Int) = uniform(1 to n)

    /**
     * Return a distribution containing only the given value.
     */
    def fixed[A](value: A) = uniform(Seq(value))

    /**
     * Return a distribution containing the given values, all with equal
     * probability.
     */
    def uniform[A](values: Iterable[A]) =
    {
        val w = 1.0 / values.size
        val xs = values map { _ -> w }
        weighted(xs.toSeq:_*).collapse
    }

    /**
     * Return a distribution containing the given value -> weight pairs. Weights
     * will be normalized, so arbitrary weights can be used.
     */
    def weighted[A, B](weightedValues: (A, B)*)(implicit n: Numeric[B]) =
        Distribution(weightedValues.map({
            case (v, w) => v -> n.toDouble(w)
        }).toArray).normalize

    /**
     * Operations dealing with distributions of sequences of values.
     */
    implicit class SeqDistributionOps[A](d: Distribution[Seq[A]])
    {
        def sum(implicit n: Numeric[A]) = d.map(_.sum)

        def nth(n: Int)(implicit num: Numeric[A]) =
            d.sorted.map(_(n))

        /**
         * Keep the `n` largest values in each sequence.
         */
        def keep(n: Int)(implicit num: Numeric[A]) =
            d.sorted.reverse.map(_.take(n).sum)

        /**
         * Keep the `n` smallest values in each sequence.
         */
        def keepLowest(n: Int)(implicit num: Numeric[A]) =
            d.sorted.map(_.take(n).sum)

        /**
         * Sort the values in each sequences.
         */
        def sorted(implicit ord: Ordering[A]) =
            d.map(_.sorted(ord))

        /**
         * Reverse the order of values in each sequence
         */
        def reverse = d.map(_.reverse)
    }
}


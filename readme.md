# Scaladice

At it's core, Scaladice is a tool for manipulating discrete probability
distributions. It was heavily influenced by [probability-monad](https://github.com/jliszka/probability-monad),
but only works with discrete probability distributions.

The major impact of this is that Scaladice maintains an exact representation
throughout manipulation, rather than manipulating the sampling function. This
does mean that certain operations become computationally expensive to compute,
but for the practical applications it was made for, performance is just fine.

## Current Version/Stability

The code isn't particularly pretty right now, and I'm thinking about some
significant changes:

- Clean up and optimize code.
  A lot of this got hacked together, and at the very least, there's some
  redundant code to refactor.
- Represent probability with rational numbers, rather than Doubles.
  Doubles are generally sufficient for things I'm doing right now, but rational
  numbers could make it better. There would be a performance hit for going this
  route, so I'm hesitant to do it.
- Switch to SBT.
  SBT seems to be more popular in the Scala world, so this might make sense. For
  now though, I'm more comfortable with Maven.

## Motivating Use Case

In full-nerd fashion, I built this as a tool for reasoning about outcomes in
tabletop games (primarily D&D).

## Examples

### Show distribution of 1d20

    val roll = Distribution.d(20)

    // the hist function returns renders a simple ASCII histogram to a String
    println(roll.hist)

#### Output:

     1  5.00% ####
     2  5.00% ####
     3  5.00% ####
     4  5.00% ####
     5  5.00% ####
     6  5.00% ####
     7  5.00% ####
     8  5.00% ####
     9  5.00% ####
    10  5.00% ####
    11  5.00% ####
    12  5.00% ####
    13  5.00% ####
    14  5.00% ####
    15  5.00% ####
    16  5.00% ####
    17  5.00% ####
    18  5.00% ####
    19  5.00% ####
    20  5.00% ####

### Show distribution of 2d6

There are multiple ways to combine dice, sometimes returning the same
distribution. Here, we show two ways of rolling `2d6` and show that the expected
outcome is the same.

    val roll1 = d(6) + d(6)
    val roll2 = d(6).repeat(2).sum

    // the ev function returns the expected value of the distribution
    println(s"${roll1.ev} == ${roll2.ev}")

#### Output:

    7.000000000000002 == 7.000000000000002

### Show distribution of 2d6, rerolling 1s and 2s

In D&D 5e, the Great Weapon Fighting style allows re-rolling 1s and 2s. Here, we
demonstrate finding the distribution of rolling damage for a Great Sword (2d6)
with and without this feature, comparing the results.

    val normal = d(6).repeat(2).sum
    println(f"Normal (${normal.ev}%.1f):\n${normal.hist}")

    val gwf = d(6).rerollWhere(_ <= 2).repeat(2).sum
    println(f"GWF (${gwf.ev}%.1f):\n${gwf.hist}")
    
#### Output:

    Normal (7.0):
     2  2.78% ##
     3  5.56% #####
     4  8.33% ########
     5 11.11% ###########
     6 13.89% #############
     7 16.67% ################
     8 13.89% #############
     9 11.11% ###########
    10  8.33% ########
    11  5.56% #####
    12  2.78% ##
    GWF (8.3):
     2  0.31%
     3  0.62%
     4  2.78% ##
     5  4.94% ####
     6  9.88% #########
     7 14.81% ##############
     8 17.28% #################
     9 19.75% ###################
    10 14.81% ##############
    11  9.88% #########
    12  4.94% ####

### Show distribution 4d6 keep 3

`4d6 keep 3` (or `4d6k3`) is a common way to roll attributes in D&D. Here we
output the histogram for a single such roll.

    val roll = d(6).repeat(4).keep(3)
    
    println(roll.hist)

#### Output:

     3  0.08%
     4  0.31%
     5  0.77%
     6  1.62% #
     7  2.93% ##
     8  4.78% ####
     9  7.02% #######
    10  9.41% #########
    11 11.42% ###########
    12 12.89% ############
    13 13.27% #############
    14 12.35% ############
    15 10.11% ##########
    16  7.25% #######
    17  4.17% ####
    18  1.62% #

### Shot expected results of rolling 6x 4d6 keep 3

The "expected" outcome for rolling attributes with 4d6k3 is
`9, 10, 12, 13, 14, 16` -- this shows how to compute that.

    // distribution for 4d6 keep 3
    val attr = d(6).repeat(4).keep(3)
    
    // combinations of 6 attrs, ignoring order (the sequences represented are
    // actually sorted, so that sequences with the same values are collapsed)
    val attrs = attr.repeatUnordered(6)
    
    val expectedAttrs = for (n <- 0 to 5)
        // take the Nth element of each sequence
        yield attrs.nth(n).ev.round
    
    println(expectedAttrs.mkString(", "))

#### Output:

    9, 10, 12, 13, 14, 16

### Roll 6x 4d6 keep 3

Here, we actually randomly sample values our distribution to generate values.

    val roll = d(6).repeat(4).keep(3)
    
    println(roll.sample(6).mkString(", "))

#### Example Outputs:

    9, 12, 13, 16, 13, 10

    13, 14, 10, 18, 13, 16

    17, 16, 7, 10, 15, 15

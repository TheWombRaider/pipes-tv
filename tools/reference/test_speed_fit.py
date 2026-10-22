"""Regression checks for the scene-length fit. Run: python -I tools/reference/test_speed_fit.py."""
import itertools
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from speed_fit import best_rate, ks


def ecdf_distance(a, b):
    return max(abs(sum(v <= x for v in a) / len(a) - sum(v <= x for v in b) / len(b))
               for x in set(a + b))


class SpeedFitTest(unittest.TestCase):
    def test_identical_samples_with_ties(self):
        self.assertEqual(ks([1, 1], [1, 1]), 0)
        self.assertEqual(ks([1, 2], [1, 2]), 0)

    def test_ties_with_unequal_sample_sizes(self):
        self.assertAlmostEqual(ks([0, 0, 1], [0, 1]), 1 / 6)
        self.assertEqual(ks([1, 1], [1, 2]), 0.5)
        self.assertEqual(ks([1, 2], [3, 4]), 1)

    def test_matches_ecdf_at_every_observed_value(self):
        samples = [list(values) for n in range(1, 5)
                   for values in itertools.combinations_with_replacement(range(3), n)]
        for a in samples:
            for b in samples:
                with self.subTest(a=a, b=b):
                    self.assertAlmostEqual(ks(a, b), ecdf_distance(a, b))
                    self.assertEqual(ks(a, b), ks(b, a))

    def test_empty_samples_are_undefined(self):
        for a, b in [([], []), ([1], []), ([], [1])]:
            with self.assertRaises(ValueError):
                ks(a, b)

    def test_rate_fit_accepts_exact_match_with_repeated_ticks(self):
        self.assertEqual(best_rate([1, 1, 2, 3], [30, 30, 60, 90]), (30, 0))


if __name__ == "__main__":
    unittest.main()

from pathlib import Path
import tempfile
import unittest

from battle_scenario import approach_timeout, battle_model_errors, pack_loaded_once_per_connection


class BattlePackLoadTest(unittest.TestCase):
    def test_rejected_battle_model_and_metadata_fail(self):
        rejected = [
            '[Worker/ERROR] Failed to load model krasnokamen_battle:models/item/iron_lance.json',
            '[Worker/WARN] Missing block model: krasnokamen_battle:item/iron_halberd',
            '[Render thread/WARN] Error reading pack metadata, attempting fallback type',
        ]
        with tempfile.TemporaryDirectory() as root:
            log = Path(root) / 'client.log'
            log.write_text('\n'.join(rejected + [
                'Missing model for variant: Block{krasnokamen_loader:loader_block}',
                'Reloading ResourceManager: vanilla',
            ]))
            self.assertEqual(battle_model_errors([log]), {str(log): rejected})

    def test_valid_pack_load_has_no_errors(self):
        with tempfile.TemporaryDirectory() as root:
            log = Path(root) / 'client.log'
            log.write_text('Reloading ResourceManager: vanilla, server/123\n')
            self.assertEqual(battle_model_errors([log]), {str(log): []})


if __name__ == '__main__':
    unittest.main()


class PackLoadedOncePerConnectionTest(unittest.TestCase):
    def test_two_connections_one_success_each_pass(self):
        self.assertTrue(pack_loaded_once_per_connection([
            {"connection": 11, "action": 0}, {"connection": 12, "action": 0}]))

    def test_one_connection_loading_twice_does_not_prove_the_other(self):
        # Red before the fix: the old count-based check summed two successes.
        self.assertFalse(pack_loaded_once_per_connection([
            {"connection": 11, "action": 0}, {"connection": 11, "action": 0}]))

    def test_a_declined_or_missing_answer_fails(self):
        self.assertFalse(pack_loaded_once_per_connection([
            {"connection": 11, "action": 0}, {"connection": 12, "action": 1}]))
        self.assertFalse(pack_loaded_once_per_connection([{"connection": 11, "action": 0}]))


class ApproachTimeoutTest(unittest.TestCase):
    def test_timeout_never_outlives_the_combat_deadline(self):
        self.assertEqual(approach_timeout(90), 15)
        self.assertEqual(approach_timeout(4.7), 4)
        self.assertEqual(approach_timeout(0.2), 1)
        # Red before the fix: the loop passed a flat 15 and could wait 14 s past the deadline.
        self.assertLessEqual(approach_timeout(2), 2)

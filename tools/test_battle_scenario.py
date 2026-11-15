from pathlib import Path
import tempfile
import unittest

from battle_scenario import approach_timeout, battle_model_errors, pack_loaded_once_per_connection, seed_report


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


class PackLoadedOncePerConnectionTest(unittest.TestCase):
    @staticmethod
    def connection(connection, actor, actions=(3, 4, 0)):
        # Login is retained even if a connection never answers the pack.
        return [{"connection": connection, "actor": actor, "action": None}] + [
            {"connection": connection, "actor": actor, "action": action} for action in actions]

    def test_two_connections_one_success_each_pass(self):
        self.assertTrue(pack_loaded_once_per_connection(
            self.connection(11, "PovRed") + self.connection(12, "CamSpec")))

    def test_reconnects_must_all_load_once(self):
        self.assertTrue(pack_loaded_once_per_connection(
            self.connection(11, "PovRed") + self.connection(12, "CamSpec")
            + self.connection(13, "PovRed")))

    def test_successful_pair_does_not_hide_declined_third_connection(self):
        self.assertFalse(pack_loaded_once_per_connection(
            self.connection(11, "PovRed") + self.connection(12, "CamSpec")
            + self.connection(13, "PovRed", (1,))))

    def test_one_actor_cannot_answer_for_the_other(self):
        self.assertFalse(pack_loaded_once_per_connection(
            self.connection(11, "PovRed") + self.connection(12, "PovRed")))

    def test_missing_failed_duplicate_or_nonterminal_success_fails(self):
        for actions in ((), (3,), (1,), (2,), (5,), (6,), (7,),
                        (0, 0), (1, 0), (2, 0), (0, 3)):
            with self.subTest(actions=actions):
                self.assertFalse(pack_loaded_once_per_connection(
                    self.connection(11, "PovRed") + self.connection(12, "CamSpec", actions)))
        self.assertFalse(pack_loaded_once_per_connection(self.connection(11, "PovRed")))

    def test_unbound_or_changed_actor_fails(self):
        self.assertFalse(pack_loaded_once_per_connection([
            {"connection": 11, "action": 0}, {"connection": 12, "action": 0}]))
        rows = self.connection(11, "PovRed") + self.connection(12, "CamSpec")
        rows[-1]["actor"] = "PovRed"
        self.assertFalse(pack_loaded_once_per_connection(rows))

    def test_unrelated_actor_cannot_supply_missing_success(self):
        self.assertFalse(pack_loaded_once_per_connection(
            self.connection(11, "PovRed") + self.connection(12, "Other")))


class ApproachTimeoutTest(unittest.TestCase):
    def test_timeout_never_outlives_the_combat_deadline(self):
        self.assertEqual(approach_timeout(90), 15)
        self.assertEqual(approach_timeout(4.7), 4)
        self.assertEqual(approach_timeout(0.2), 1)
        # Red before the fix: the loop passed a flat 15 and could wait 14 s past the deadline.
        self.assertLessEqual(approach_timeout(2), 2)


class SeedReportTest(unittest.TestCase):
    def test_receipt_names_the_seed_the_gate_ran(self):
        self.assertEqual(seed_report(42, 935), {"seed": 42, "world_seed": 935})
        # Red before the fix: java-battle.json carried no seed, so a red gate was not reproducible.
        self.assertIn("seed", seed_report(7, 935))


if __name__ == '__main__':
    unittest.main()

package com.cardwise.cardwise_backend;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Runs V1-V12 in a throwaway schema, so no persistent data is ever modified.
@SpringBootTest
@ActiveProfiles("test")
class V12ActivationMigrationTest {

    private static final String TANGERINE_CARD =
            "SELECT id FROM {s}.credit_cards WHERE issuer = 'Tangerine'";

    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbc;
    private String schema;

    @BeforeEach
    void createIsolatedSchema() {
        jdbc = new JdbcTemplate(dataSource);
        schema = "v12_test_" + UUID.randomUUID().toString().replace("-", "");
    }

    @AfterEach
    void dropIsolatedSchema() {
        jdbc.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
    }

    private Flyway flywayTo(String version) {
        return Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .locations("classpath:db/migration")
                .target(version)
                .load();
    }

    private String sql(String template) {
        return template.replace("{s}", schema);
    }

    private int count(String template) {
        Integer value = jdbc.queryForObject(sql(template), Integer.class);
        return value == null ? 0 : value;
    }

    @Test
    void v12ActivatesExactlyTheTangerineCardAndNothingElse() {
        flywayTo("11").migrate();

        assertEquals(1, count(
                "SELECT count(*) FROM {s}.credit_cards WHERE catalogue_withheld"));
        assertEquals(1, count(
                "SELECT count(*) FROM {s}.credit_cards "
                        + "WHERE catalogue_withheld AND issuer = 'Tangerine'"));

        flywayTo("12").migrate();

        assertEquals(0, count(
                "SELECT count(*) FROM {s}.credit_cards WHERE catalogue_withheld"));
        assertEquals(5, count("SELECT count(*) FROM {s}.credit_cards"));
        assertEquals(1, count(
                "SELECT count(*) FROM {s}.flyway_schema_history "
                        + "WHERE version = '12' AND success"));
    }

    @Test
    void freshDatabaseWithAllMigrationsHasBothRealCardsActive() {
        flywayTo("12").migrate();

        assertEquals(2, count(
                "SELECT count(*) FROM {s}.credit_cards "
                        + "WHERE is_demo = FALSE AND NOT catalogue_withheld"));
        assertEquals(8, count(
                "SELECT count(*) FROM {s}.reward_rules r JOIN {s}.credit_cards c "
                        + "ON c.id = r.credit_card_id WHERE c.issuer = 'Tangerine'"));
    }

    static Stream<Arguments> invalidStates() {
        String card = "(" + TANGERINE_CARD + ")";
        return Stream.of(
                Arguments.of("Tangerine card missing",
                        "DELETE FROM {s}.reward_rules WHERE credit_card_id IN " + card
                                + "; DELETE FROM {s}.credit_cards WHERE issuer = 'Tangerine'",
                        "expected exactly one Tangerine"),
                Arguments.of("duplicate Tangerine card",
                        "INSERT INTO {s}.credit_cards (card_name, issuer, reward_type, "
                                + "annual_fee, is_demo) VALUES ('Tangerine Money-Back Credit "
                                + "Card', 'Tangerine', 'CASHBACK', 0, FALSE)",
                        "expected exactly one Tangerine"),
                Arguments.of("non-zero annual fee",
                        "UPDATE {s}.credit_cards SET annual_fee = 10 WHERE issuer = 'Tangerine'",
                        "zero-fee"),
                Arguments.of("selection policy missing",
                        "DELETE FROM {s}.card_selection_policies WHERE credit_card_id IN " + card,
                        "selection policy"),
                Arguments.of("wrong base selection limit",
                        "UPDATE {s}.card_selection_policies SET base_selection_limit = 1 "
                                + "WHERE credit_card_id IN " + card,
                        "selection policy"),
                Arguments.of("wrong extended selection limit",
                        "UPDATE {s}.card_selection_policies SET extended_selection_limit = 2 "
                                + "WHERE credit_card_id IN " + card,
                        "selection policy"),
                Arguments.of("wrong hold days",
                        "UPDATE {s}.card_selection_policies SET change_hold_days = 60 "
                                + "WHERE credit_card_id IN " + card,
                        "selection policy"),
                Arguments.of("wrong requirement code",
                        "UPDATE {s}.card_selection_policies SET extended_requirement = 'OTHER' "
                                + "WHERE credit_card_id IN " + card,
                        "selection policy"),
                Arguments.of("reward rule missing",
                        "DELETE FROM {s}.reward_rules WHERE spending_category = 'GAS' "
                                + "AND credit_card_id IN " + card,
                        "expected 8 reward rules"),
                Arguments.of("unexpected extra reward rule",
                        "INSERT INTO {s}.reward_rules (credit_card_id, spending_category, "
                                + "reward_rate) SELECT id, 'EXTRA', 0.5 FROM {s}.credit_cards "
                                + "WHERE issuer = 'Tangerine'",
                        "expected 8 reward rules"),
                Arguments.of("wrong selected rate",
                        "UPDATE {s}.reward_rules SET reward_rate = 1.5 "
                                + "WHERE spending_category = 'GAS' AND credit_card_id IN " + card,
                        "selectable rules"),
                Arguments.of("wrong unselected rate",
                        "UPDATE {s}.reward_rules SET unselected_reward_rate = 0.25 "
                                + "WHERE spending_category = 'DINING' AND credit_card_id IN " + card,
                        "selectable rules"),
                Arguments.of("selectable category no longer selectable",
                        "UPDATE {s}.reward_rules SET is_selectable = FALSE, "
                                + "unselected_reward_rate = NULL "
                                + "WHERE spending_category = 'TRANSIT' AND credit_card_id IN " + card,
                        "selectable rules"),
                Arguments.of("fixed rule with a wrong rate",
                        "UPDATE {s}.reward_rules SET reward_rate = 1.0 "
                                + "WHERE spending_category = 'OTHER' AND credit_card_id IN " + card,
                        "fixed rules"),
                Arguments.of("fixed category made selectable",
                        "UPDATE {s}.reward_rules SET is_selectable = TRUE, "
                                + "unselected_reward_rate = 0.5 "
                                + "WHERE spending_category = 'EV_CHARGING' AND credit_card_id IN " + card,
                        "fixed rules")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidStates")
    void v12FailsAndRollsBackWhenAPrerequisiteIsNotMet(
            String description, String corruption, String expectedFragment) {
        flywayTo("11").migrate();
        jdbc.execute(sql(corruption));
        int withheldBefore = count(
                "SELECT count(*) FROM {s}.credit_cards WHERE catalogue_withheld");

        FlywayException failure = assertThrows(
                FlywayException.class, () -> flywayTo("12").migrate());

        String message = failure.getMessage();
        assertTrue(message.contains("V12 activation blocked"), message);
        assertTrue(message.contains(expectedFragment), message);

        // The whole migration rolled back: nothing recorded, nothing activated.
        assertEquals(0, count(
                "SELECT count(*) FROM {s}.flyway_schema_history WHERE version = '12'"));
        assertEquals(withheldBefore, count(
                "SELECT count(*) FROM {s}.credit_cards WHERE catalogue_withheld"));
    }
}

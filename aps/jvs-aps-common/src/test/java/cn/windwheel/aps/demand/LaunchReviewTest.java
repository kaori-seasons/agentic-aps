package cn.windwheel.aps.demand;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

class LaunchReviewTest {
    private LaunchReview.Analog analog(
            String id, int observed, boolean stockout, boolean promotion) {
        return new LaunchReview.Analog(
                id, observed, 30, new BigDecimal("600"), true, stockout, promotion, "同类产品");
    }

    @Test
    void onlyCompleteUncensoredNonPromotionalProductsEnterReference() {
        var result =
                LaunchReview.evidence(
                        List.of(
                                analog("normal", 30, false, false),
                                analog("partial", 10, false, false),
                                analog("stockout", 30, true, false),
                                analog("promotion", 30, false, true)));
        assertEquals(0, result.weeklyReference().compareTo(new BigDecimal("140")));
        assertEquals(3, result.warnings().size());
    }

    @Test
    void noEvidenceReturnsMissingReferenceInsteadOfZeroDemand() {
        assertNull(
                LaunchReview.evidence(List.of(analog("stockout", 30, true, false)))
                        .weeklyReference());
    }

    @Test
    void duplicateProductsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        LaunchReview.evidence(
                                List.of(
                                        analog("same", 30, false, false),
                                        analog("same", 30, false, false))));
    }

    @Test
    void expertBoundsValidated() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        LaunchReview.validate(
                                new LaunchReview.Vote(
                                        BigDecimal.TEN,
                                        BigDecimal.ONE,
                                        BigDecimal.TEN,
                                        "依据",
                                        "无")));
    }

    @Test
    void anonymousMedianResistsOneExtremeEstimate() {
        var votes = List.of(vote("10"), vote("12"), vote("9999"));
        assertEquals(0, LaunchReview.summarize(votes).median().compareTo(new BigDecimal("12")));
    }

    @Test
    void minimumThreePeopleAndEvenMedian() {
        assertThrows(
                IllegalArgumentException.class,
                () -> LaunchReview.summarize(List.of(vote("10"), vote("12"))));
        assertEquals(
                0,
                LaunchReview.summarize(List.of(vote("10"), vote("12"), vote("14"), vote("16")))
                        .median()
                        .compareTo(new BigDecimal("13")));
    }

    private LaunchReview.Vote vote(String n) {
        return new LaunchReview.Vote(
                BigDecimal.ZERO, new BigDecimal(n), new BigDecimal(n), "依据", "无");
    }
}

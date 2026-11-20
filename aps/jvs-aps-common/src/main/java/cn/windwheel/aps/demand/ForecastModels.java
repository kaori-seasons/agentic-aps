package cn.windwheel.aps.demand;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

final class ForecastModels {
    enum Method {
        MEAN4,
        LAST,
        LEVEL,
        DAMPED,
        SBA,
        YEAR,
        SEASONAL_LEVEL
    }

    record Score(Method method, double absoluteError, double biasUnits, double actual, int folds) {
        public Double wape() {
            return actual == 0 ? null : absoluteError / actual;
        }
    }

    record Fold(List<Double> knownHistory, List<Double> actuals) {}

    record Selection(Method method, List<Score> scores) {}

    static Selection select(List<Fold> folds) {
        List<Score> scores = new ArrayList<>();
        Method best = Method.MEAN4;
        double bestError = Double.POSITIVE_INFINITY;
        for (Method method : Method.values()) {
            double absolute = 0, signed = 0, total = 0;
            int evaluated = 0, wins = 0;
            for (Fold fold : folds) {
                List<Double> h = fold.knownHistory;
                if (method == Method.SEASONAL_LEVEL && h.size() < 104) continue;
                if (method == Method.YEAR && h.size() < 52) continue;
                if (method == Method.SBA && h.stream().filter(n -> n == 0).count() * 3 < h.size())
                    continue;
                double foldLoss = 0, baseLoss = 0;
                for (int k = 0; k < fold.actuals.size(); k++) {
                    double actual = fold.actuals.get(k);
                    double error = predict(method, h, k + 1) - actual;
                    foldLoss += Math.abs(error);
                    baseLoss += Math.abs(predict(Method.MEAN4, h, k + 1) - actual);
                    absolute += Math.abs(error);
                    signed += error;
                    total += actual;
                }
                if (foldLoss < baseLoss) wins++;
                evaluated++;
            }
            if (evaluated != folds.size() || evaluated < 3) continue;
            scores.add(new Score(method, absolute, signed, total, evaluated));
            if ((method == Method.MEAN4 || wins * 2 > evaluated) && absolute < bestError) {
                bestError = absolute;
                best = method;
            }
        }
        return new Selection(best, scores);
    }

    static BigDecimal quantity(Method method, List<Double> history, int horizon) {
        return BigDecimal.valueOf(predict(method, history, horizon))
                .setScale(6, RoundingMode.HALF_UP);
    }

    private static double predict(Method method, List<Double> h, int step) {
        if (h.isEmpty()) return 0;
        int n = h.size();
        switch (method) {
            case LAST:
                return h.get(n - 1);
            case SEASONAL_LEVEL:
                int lastSeason = n - 52 + (step - 1) % 52;
                return .65 * h.get(lastSeason) + .35 * h.get(lastSeason - 52);
            case YEAR:
                return h.get(n - 52 + (step - 1) % 52);
            case MEAN4:
                return h.subList(Math.max(0, n - 4), n).stream()
                        .mapToDouble(Double::doubleValue)
                        .average()
                        .orElse(0);
            case SBA:
                double size = 0, interval = 0;
                int previous = -1;
                for (int i = 0; i < n; i++) {
                    if (h.get(i) <= 0) continue;
                    size = previous < 0 ? h.get(i) : .2 * h.get(i) + .8 * size;
                    interval = previous < 0 ? i + 1 : .2 * (i - previous) + .8 * interval;
                    previous = i;
                }
                return interval == 0 ? 0 : .9 * size / interval;
            default:
                double level = h.get(0), trend = 0;
                for (int i = 1; i < n; i++) {
                    double old = level;
                    level = .3 * h.get(i) + .7 * (level + .8 * trend);
                    if (method == Method.DAMPED) trend = .2 * (level - old) + .8 * .8 * trend;
                }
                return Math.max(0, level + 4 * (1 - Math.pow(.8, step)) * trend);
        }
    }
}

ALTER TABLE topic_report_rule
  ADD COLUMN monthly_start_year SMALLINT UNSIGNED NULL AFTER monthly_enabled,
  ADD COLUMN monthly_start_period TINYINT UNSIGNED NULL AFTER monthly_start_year,
  ADD COLUMN monthly_end_year SMALLINT UNSIGNED NULL AFTER monthly_start_period,
  ADD COLUMN monthly_end_period TINYINT UNSIGNED NULL AFTER monthly_end_year,
  ADD COLUMN quarterly_start_year SMALLINT UNSIGNED NULL AFTER quarterly_enabled,
  ADD COLUMN quarterly_start_period TINYINT UNSIGNED NULL AFTER quarterly_start_year,
  ADD COLUMN quarterly_end_year SMALLINT UNSIGNED NULL AFTER quarterly_start_period,
  ADD COLUMN quarterly_end_period TINYINT UNSIGNED NULL AFTER quarterly_end_year;

UPDATE topic_report_rule
SET monthly_start_year=effective_year,
    monthly_start_period=1,
    monthly_end_year=effective_year,
    monthly_end_period=12,
    quarterly_start_year=effective_year,
    quarterly_start_period=1,
    quarterly_end_year=effective_year,
    quarterly_end_period=4;

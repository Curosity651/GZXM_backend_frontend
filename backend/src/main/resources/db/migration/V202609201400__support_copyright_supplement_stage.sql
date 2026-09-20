ALTER TABLE achievement
  DROP CHECK chk_achievement_indicator_state;

ALTER TABLE achievement
  ADD CONSTRAINT chk_achievement_indicator_state
  CHECK (
    counts_to_indicator = 0
    OR status IN (
      'WAIT_PUBLICATION',
      'WAIT_GRANT',
      'WAIT_CERTIFICATE',
      'SUPPLEMENT_INITIAL',
      'SUPPLEMENT_FINAL',
      'SUPPLEMENT_RETURNED',
      'EFFECTIVE'
    )
  );

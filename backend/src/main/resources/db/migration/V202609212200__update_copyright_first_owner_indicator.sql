UPDATE indicator_definition
SET name = '第一著作权人是广西电网的软著数量',
    match_rule = JSON_OBJECT('field', 'isPowerGridFirstCopyrightOwner', 'equals', TRUE)
WHERE code = 'POWER_GRID_FIRST_COMPLETER_COPYRIGHT';

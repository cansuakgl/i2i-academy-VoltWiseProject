SET search_path TO wattsmart, public;

CREATE TABLE appliance_model_profiles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    appliance_type_id UUID NOT NULL REFERENCES appliance_types(id) ON DELETE CASCADE,
    manufacturer TEXT NOT NULL,
    model_name TEXT NOT NULL,
    display_name TEXT,
    nominal_wattage NUMERIC(12, 2),
    safe_watt_limit NUMERIC(12, 2),
    peak_watt_limit NUMERIC(12, 2),
    source_name TEXT,
    source_url TEXT,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_appliance_model_profile
        UNIQUE (appliance_type_id, manufacturer, model_name),
    CONSTRAINT chk_appliance_model_nominal_wattage_non_negative
        CHECK (nominal_wattage IS NULL OR nominal_wattage >= 0),
    CONSTRAINT chk_appliance_model_safe_watt_limit_positive
        CHECK (safe_watt_limit IS NULL OR safe_watt_limit > 0),
    CONSTRAINT chk_appliance_model_peak_watts_valid
        CHECK (
            peak_watt_limit IS NULL
            OR safe_watt_limit IS NULL
            OR peak_watt_limit >= safe_watt_limit
        )
);

CREATE INDEX idx_appliance_model_profiles_type
    ON appliance_model_profiles (appliance_type_id);

CREATE TRIGGER trg_appliance_model_profiles_set_updated_at
BEFORE UPDATE ON appliance_model_profiles
FOR EACH ROW
EXECUTE FUNCTION wattsmart.set_updated_at();

WITH seed_tariffs (
    code,
    name,
    description,
    currency_code,
    base_rate_per_kwh,
    effective_from,
    is_active
) AS (
    VALUES
        (
            'RESIDENTIAL_STANDARD_TR',
            'Residential Standard TR',
            'Default household tariff profile for residential monitoring demos.',
            'TRY',
            2.250000::NUMERIC,
            DATE '2026-01-01',
            TRUE
        ),
        (
            'RESIDENTIAL_ECO_TR',
            'Residential Eco Saver TR',
            'Lower base-rate profile for efficient residential usage.',
            'TRY',
            1.950000::NUMERIC,
            DATE '2026-01-01',
            TRUE
        ),
        (
            'RESIDENTIAL_PEAK_TR',
            'Residential Peak Protection TR',
            'Higher base-rate profile with stronger penalties for peak-heavy homes.',
            'TRY',
            2.600000::NUMERIC,
            DATE '2026-01-01',
            TRUE
        )
)
INSERT INTO tariff_plans (
    code,
    name,
    description,
    currency_code,
    base_rate_per_kwh,
    effective_from,
    is_active
)
SELECT
    code,
    name,
    description,
    currency_code,
    base_rate_per_kwh,
    effective_from,
    is_active
FROM seed_tariffs
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    description = EXCLUDED.description,
    currency_code = EXCLUDED.currency_code,
    base_rate_per_kwh = EXCLUDED.base_rate_per_kwh,
    effective_from = EXCLUDED.effective_from,
    is_active = EXCLUDED.is_active,
    updated_at = NOW();

WITH milestone_seed (
    tariff_code,
    milestone,
    stage,
    penalty_multiplier
) AS (
    VALUES
        ('RESIDENTIAL_STANDARD_TR', 'PCT_80'::usage_percentage_milestone, 'WARNING'::milestone_stage, NULL::NUMERIC),
        ('RESIDENTIAL_STANDARD_TR', 'PCT_100'::usage_percentage_milestone, 'WARNING'::milestone_stage, NULL::NUMERIC),
        ('RESIDENTIAL_STANDARD_TR', 'PCT_120'::usage_percentage_milestone, 'PENALTY'::milestone_stage, 1.1500::NUMERIC),
        ('RESIDENTIAL_STANDARD_TR', 'PCT_130'::usage_percentage_milestone, 'PENALTY'::milestone_stage, 1.2500::NUMERIC),
        ('RESIDENTIAL_STANDARD_TR', 'PCT_150'::usage_percentage_milestone, 'PENALTY'::milestone_stage, 1.5000::NUMERIC),
        ('RESIDENTIAL_STANDARD_TR', 'PCT_180'::usage_percentage_milestone, 'PENALTY'::milestone_stage, 2.0000::NUMERIC),
        ('RESIDENTIAL_ECO_TR', 'PCT_80'::usage_percentage_milestone, 'WARNING'::milestone_stage, NULL::NUMERIC),
        ('RESIDENTIAL_ECO_TR', 'PCT_100'::usage_percentage_milestone, 'WARNING'::milestone_stage, NULL::NUMERIC),
        ('RESIDENTIAL_ECO_TR', 'PCT_120'::usage_percentage_milestone, 'PENALTY'::milestone_stage, 1.1000::NUMERIC),
        ('RESIDENTIAL_ECO_TR', 'PCT_130'::usage_percentage_milestone, 'PENALTY'::milestone_stage, 1.2000::NUMERIC),
        ('RESIDENTIAL_ECO_TR', 'PCT_150'::usage_percentage_milestone, 'PENALTY'::milestone_stage, 1.4000::NUMERIC),
        ('RESIDENTIAL_ECO_TR', 'PCT_180'::usage_percentage_milestone, 'PENALTY'::milestone_stage, 1.8000::NUMERIC),
        ('RESIDENTIAL_PEAK_TR', 'PCT_80'::usage_percentage_milestone, 'WARNING'::milestone_stage, NULL::NUMERIC),
        ('RESIDENTIAL_PEAK_TR', 'PCT_100'::usage_percentage_milestone, 'WARNING'::milestone_stage, NULL::NUMERIC),
        ('RESIDENTIAL_PEAK_TR', 'PCT_120'::usage_percentage_milestone, 'PENALTY'::milestone_stage, 1.2000::NUMERIC),
        ('RESIDENTIAL_PEAK_TR', 'PCT_130'::usage_percentage_milestone, 'PENALTY'::milestone_stage, 1.3500::NUMERIC),
        ('RESIDENTIAL_PEAK_TR', 'PCT_150'::usage_percentage_milestone, 'PENALTY'::milestone_stage, 1.6500::NUMERIC),
        ('RESIDENTIAL_PEAK_TR', 'PCT_180'::usage_percentage_milestone, 'PENALTY'::milestone_stage, 2.2500::NUMERIC)
)
INSERT INTO tariff_plan_milestones (
    tariff_plan_id,
    milestone,
    stage,
    penalty_multiplier
)
SELECT
    tariff_plans.id,
    milestone_seed.milestone,
    milestone_seed.stage,
    milestone_seed.penalty_multiplier
FROM milestone_seed
JOIN tariff_plans
    ON tariff_plans.code = milestone_seed.tariff_code
ON CONFLICT (tariff_plan_id, milestone) DO UPDATE
SET stage = EXCLUDED.stage,
    penalty_multiplier = EXCLUDED.penalty_multiplier,
    updated_at = NOW();

WITH appliance_type_seed (
    code,
    display_name,
    description,
    typical_watts,
    default_safe_watt_limit,
    peak_watt_limit
) AS (
    VALUES
        ('REFRIGERATOR', 'Refrigerator', 'Cold storage appliance with intermittent compressor draw.', 150.00::NUMERIC, 350.00::NUMERIC, 700.00::NUMERIC),
        ('FREEZER', 'Freezer', 'Standalone freezer appliance with intermittent compressor draw.', 180.00::NUMERIC, 450.00::NUMERIC, 800.00::NUMERIC),
        ('WASHING_MACHINE', 'Washing Machine', 'Laundry appliance with motor and water-heating phases.', 500.00::NUMERIC, 1500.00::NUMERIC, 2200.00::NUMERIC),
        ('DRYER', 'Dryer', 'Laundry dryer with sustained heating and motor load.', 900.00::NUMERIC, 1800.00::NUMERIC, 2600.00::NUMERIC),
        ('DISHWASHER', 'Dishwasher', 'Kitchen appliance with pump, heating, and drying phases.', 1200.00::NUMERIC, 1800.00::NUMERIC, 2400.00::NUMERIC),
        ('OVEN', 'Electric Oven', 'High-draw cooking appliance with heating elements.', 2200.00::NUMERIC, 3200.00::NUMERIC, 4000.00::NUMERIC),
        ('COOKTOP', 'Electric Cooktop', 'Countertop or built-in electric cooking hob.', 1800.00::NUMERIC, 3000.00::NUMERIC, 7200.00::NUMERIC),
        ('MICROWAVE', 'Microwave Oven', 'Compact cooking appliance with short high-draw cycles.', 900.00::NUMERIC, 1400.00::NUMERIC, 1800.00::NUMERIC),
        ('KETTLE', 'Electric Kettle', 'Fast-boil small kitchen appliance.', 2000.00::NUMERIC, 2400.00::NUMERIC, 3000.00::NUMERIC),
        ('COFFEE_MACHINE', 'Coffee Machine', 'Small kitchen appliance for espresso or filter coffee.', 1200.00::NUMERIC, 1600.00::NUMERIC, 2200.00::NUMERIC),
        ('AIR_CONDITIONER', 'Air Conditioner', 'Cooling and heating appliance with compressor-driven load.', 1500.00::NUMERIC, 2600.00::NUMERIC, 3500.00::NUMERIC),
        ('AIR_PURIFIER', 'Air Purifier', 'Air quality appliance with fan-driven continuous load.', 45.00::NUMERIC, 120.00::NUMERIC, 180.00::NUMERIC),
        ('DEHUMIDIFIER', 'Dehumidifier', 'Humidity-control appliance with compressor and fan load.', 350.00::NUMERIC, 650.00::NUMERIC, 900.00::NUMERIC),
        ('WATER_HEATER', 'Water Heater', 'Domestic hot-water appliance with sustained heating draw.', 1500.00::NUMERIC, 2500.00::NUMERIC, 3500.00::NUMERIC),
        ('HEAT_PUMP', 'Heat Pump', 'Efficient heating and cooling equipment with variable compressor load.', 1800.00::NUMERIC, 3200.00::NUMERIC, 4200.00::NUMERIC),
        ('SPACE_HEATER', 'Space Heater', 'Portable electric resistance heater.', 1800.00::NUMERIC, 2500.00::NUMERIC, 3200.00::NUMERIC),
        ('EV_CHARGER', 'EV Charger', 'Residential electric vehicle charger circuit.', 7000.00::NUMERIC, 7500.00::NUMERIC, 11000.00::NUMERIC),
        ('LIGHTING', 'Lighting Circuit', 'Grouped residential lighting load.', 300.00::NUMERIC, 800.00::NUMERIC, 1200.00::NUMERIC),
        ('TELEVISION', 'Television', 'Entertainment display appliance.', 120.00::NUMERIC, 300.00::NUMERIC, 500.00::NUMERIC),
        ('GAME_CONSOLE', 'Game Console', 'Entertainment console with variable graphics load.', 180.00::NUMERIC, 350.00::NUMERIC, 500.00::NUMERIC),
        ('COMPUTER', 'Computer Workstation', 'Desktop or workstation computing load.', 250.00::NUMERIC, 600.00::NUMERIC, 900.00::NUMERIC),
        ('LAPTOP', 'Laptop', 'Portable computer and charging load.', 65.00::NUMERIC, 140.00::NUMERIC, 240.00::NUMERIC),
        ('ROUTER', 'Network Router', 'Always-on network equipment.', 15.00::NUMERIC, 50.00::NUMERIC, 80.00::NUMERIC),
        ('HOOD', 'Cooker Hood', 'Kitchen ventilation appliance.', 180.00::NUMERIC, 350.00::NUMERIC, 700.00::NUMERIC),
        ('VACUUM_CLEANER', 'Vacuum Cleaner', 'Portable cleaning appliance with motor-driven draw.', 900.00::NUMERIC, 1400.00::NUMERIC, 2000.00::NUMERIC),
        ('ROBOT_VACUUM', 'Robot Vacuum', 'Autonomous cleaning appliance and charging dock.', 45.00::NUMERIC, 120.00::NUMERIC, 220.00::NUMERIC),
        ('IRON', 'Iron', 'Small high-draw garment-care appliance.', 2000.00::NUMERIC, 2600.00::NUMERIC, 3200.00::NUMERIC)
)
INSERT INTO appliance_types (
    code,
    display_name,
    description,
    typical_watts,
    default_safe_watt_limit,
    peak_watt_limit
)
SELECT
    code,
    display_name,
    description,
    typical_watts,
    default_safe_watt_limit,
    peak_watt_limit
FROM appliance_type_seed
ON CONFLICT (code) DO UPDATE
SET display_name = EXCLUDED.display_name,
    description = EXCLUDED.description,
    typical_watts = EXCLUDED.typical_watts,
    default_safe_watt_limit = EXCLUDED.default_safe_watt_limit,
    peak_watt_limit = EXCLUDED.peak_watt_limit,
    updated_at = NOW();

WITH appliance_model_seed (
    type_code,
    manufacturer,
    model_name,
    display_name,
    nominal_wattage,
    safe_watt_limit,
    peak_watt_limit,
    source_name,
    source_url,
    notes
) AS (
    VALUES
        (
            'REFRIGERATOR',
            'Samsung',
            'RM90F66CEW/TR',
            'Samsung RM90F66CEW 658L 4-door refrigerator',
            180.00::NUMERIC,
            450.00::NUMERIC,
            800.00::NUMERIC,
            'Samsung Türkiye',
            'https://www.samsung.com/tr/refrigerators/french-door/rm90f-36-4-door-french-door-refrigerators-with-ai-home-and-ai-hybrid-cooling-658l-clean-white-rm90f66cewtr/',
            'Model identity and capacity are seeded from the manufacturer page; watt thresholds are WattSmart defaults for monitoring.'
        ),
        (
            'DISHWASHER',
            'Bosch',
            'SMS6ECI83T',
            'Bosch Serie 6 solo dishwasher 60 cm',
            1200.00::NUMERIC,
            1800.00::NUMERIC,
            2400.00::NUMERIC,
            'Bosch Ev Aletleri Türkiye',
            'https://www.bosch-home.com.tr/tr/category/bulasik-makineleri',
            'Model identity is seeded from Bosch public catalog listings; watt thresholds are WattSmart defaults for monitoring.'
        ),
        (
            'DISHWASHER',
            'Bosch',
            'SMI8ZDS81T',
            'Bosch Serie 8 semi-integrated dishwasher 60 cm',
            1200.00::NUMERIC,
            1800.00::NUMERIC,
            2400.00::NUMERIC,
            'Bosch Ev Aletleri Türkiye',
            'https://www.bosch-home.com.tr/tr/category/bulasik-makineleri',
            'Model identity is seeded from Bosch public catalog listings; watt thresholds are WattSmart defaults for monitoring.'
        ),
        (
            'HOOD',
            'Bosch',
            'DWF65AJ60T',
            'Bosch Serie 4 wall-mounted cooker hood 60 cm',
            180.00::NUMERIC,
            350.00::NUMERIC,
            700.00::NUMERIC,
            'Bosch Ev Aletleri Türkiye',
            'https://www.bosch-home.com.tr/tr/product/ankastre-urunler/ankastre-davlumbazlar/DWF65AJ60T',
            'Model identity and public technical summary are seeded from the manufacturer page; watt thresholds are WattSmart defaults for monitoring.'
        ),
        (
            'AIR_CONDITIONER',
            'Samsung',
            'AR12TXHZAWK/SK',
            'Samsung wall-mounted air conditioner 12000 BTU class',
            1500.00::NUMERIC,
            2600.00::NUMERIC,
            3500.00::NUMERIC,
            'Samsung Türkiye',
            'https://www.samsung.com/tr/air-conditioners/',
            'Representative model profile for demo selection; watt thresholds are WattSmart defaults for monitoring.'
        ),
        ('REFRIGERATOR', 'Samsung', 'RB50DG602EB1TR', 'Samsung RB50DG602EB1TR bottom-freezer refrigerator', 160.00::NUMERIC, 420.00::NUMERIC, 750.00::NUMERIC, 'Samsung Turkiye', 'https://www.samsung.com/tr/refrigerators/', 'Catalog model profile; watt thresholds are WattSmart monitoring defaults.'),
        ('REFRIGERATOR', 'LG', 'GN-H702HLHU', 'LG GN-H702HLHU no-frost refrigerator', 170.00::NUMERIC, 430.00::NUMERIC, 780.00::NUMERIC, 'LG Turkiye', 'https://www.lg.com/tr/buzdolabi/', 'Catalog model profile; watt thresholds are WattSmart monitoring defaults.'),
        ('REFRIGERATOR', 'Arcelik', '270531 EI', 'Arcelik 270531 EI refrigerator', 160.00::NUMERIC, 420.00::NUMERIC, 760.00::NUMERIC, 'Arcelik', 'https://www.arcelik.com.tr/buzdolabi', 'Catalog model profile; watt thresholds are WattSmart monitoring defaults.'),
        ('REFRIGERATOR', 'Beko', '970475 MB', 'Beko 970475 MB refrigerator', 160.00::NUMERIC, 420.00::NUMERIC, 760.00::NUMERIC, 'Beko', 'https://www.beko.com/tr-tr/buzdolabi', 'Catalog model profile; watt thresholds are WattSmart monitoring defaults.'),
        ('FREEZER', 'Bosch', 'GSN36VIF0N', 'Bosch Serie 4 upright freezer', 180.00::NUMERIC, 450.00::NUMERIC, 800.00::NUMERIC, 'Bosch Turkiye', 'https://www.bosch-home.com.tr/tr/category/buzdolaplari-ve-derin-dondurucular', 'Catalog model profile; watt thresholds are WattSmart monitoring defaults.'),
        ('FREEZER', 'Ugur', 'UED 7266 DTK', 'Ugur UED 7266 DTK upright freezer', 190.00::NUMERIC, 470.00::NUMERIC, 820.00::NUMERIC, 'Ugur', 'https://www.ugur.com.tr/', 'Catalog model profile; watt thresholds are WattSmart monitoring defaults.'),
        ('WASHING_MACHINE', 'Bosch', 'WGA142X0TR', 'Bosch Serie 4 washing machine 9 kg', 500.00::NUMERIC, 1500.00::NUMERIC, 2200.00::NUMERIC, 'Bosch Turkiye', 'https://www.bosch-home.com.tr/tr/category/camasir-makineleri', 'Catalog model profile; watt thresholds are WattSmart monitoring defaults.'),
        ('WASHING_MACHINE', 'Samsung', 'WW90T4020CE/AH', 'Samsung WW90T4020CE washing machine 9 kg', 500.00::NUMERIC, 1500.00::NUMERIC, 2200.00::NUMERIC, 'Samsung Turkiye', 'https://www.samsung.com/tr/washers-and-dryers/washing-machines/', 'Catalog model profile; watt thresholds are WattSmart monitoring defaults.'),
        ('WASHING_MACHINE', 'LG', 'F4V5RYP2T', 'LG F4V5RYP2T washing machine', 520.00::NUMERIC, 1500.00::NUMERIC, 2200.00::NUMERIC, 'LG Turkiye', 'https://www.lg.com/tr/camasir-makinesi/', 'Catalog model profile; watt thresholds are WattSmart monitoring defaults.'),
        ('WASHING_MACHINE', 'Arcelik', '10120 M', 'Arcelik 10120 M washing machine', 520.00::NUMERIC, 1500.00::NUMERIC, 2200.00::NUMERIC, 'Arcelik', 'https://www.arcelik.com.tr/camasir-makinesi', 'Catalog model profile; watt thresholds are WattSmart monitoring defaults.'),
        ('DRYER', 'Bosch', 'WQG241A0TR', 'Bosch Serie 6 heat pump dryer 9 kg', 900.00::NUMERIC, 1800.00::NUMERIC, 2600.00::NUMERIC, 'Bosch Turkiye', 'https://www.bosch-home.com.tr/tr/category/camasir-kurutma-makineleri', 'Catalog model profile; watt thresholds are WattSmart monitoring defaults.'),
        ('DRYER', 'Samsung', 'DV90T5240AX/AH', 'Samsung DV90T5240AX heat pump dryer 9 kg', 900.00::NUMERIC, 1800.00::NUMERIC, 2600.00::NUMERIC, 'Samsung Turkiye', 'https://www.samsung.com/tr/washers-and-dryers/dryers/', 'Catalog model profile; watt thresholds are WattSmart monitoring defaults.'),
        ('DRYER', 'Arcelik', '900 KM', 'Arcelik 900 KM dryer', 900.00::NUMERIC, 1800.00::NUMERIC, 2600.00::NUMERIC, 'Arcelik', 'https://www.arcelik.com.tr/kurutma-makinesi', 'Catalog model profile; watt thresholds are WattSmart monitoring defaults.'),
        ('DISHWASHER', 'Siemens', 'SN23IW60KT', 'Siemens iQ300 dishwasher 60 cm', 1200.00::NUMERIC, 1800.00::NUMERIC, 2400.00::NUMERIC, 'Siemens Turkiye', 'https://www.siemens-home.bsh-group.com/tr/', 'Catalog model profile; watt thresholds are WattSmart monitoring defaults.'),
        ('DISHWASHER', 'Arcelik', '6144', 'Arcelik 6144 dishwasher', 1200.00::NUMERIC, 1800.00::NUMERIC, 2400.00::NUMERIC, 'Arcelik', 'https://www.arcelik.com.tr/bulasik-makinesi', 'Catalog model profile; watt thresholds are WattSmart monitoring defaults.'),
        ('DISHWASHER', 'Beko', 'BM 6046 B', 'Beko BM 6046 B dishwasher', 1200.00::NUMERIC, 1800.00::NUMERIC, 2400.00::NUMERIC, 'Beko', 'https://www.beko.com/tr-tr/bulasik-makinesi', 'Catalog model profile; watt thresholds are WattSmart monitoring defaults.'),
        ('OVEN', 'Bosch', 'HBF534EB0T', 'Bosch Serie 4 built-in oven', 2200.00::NUMERIC, 3200.00::NUMERIC, 4000.00::NUMERIC, 'Bosch Turkiye', 'https://www.bosch-home.com.tr/tr/category/ankastre-firinlar', 'Catalog model profile; watt thresholds are WattSmart monitoring defaults.'),
        ('OVEN', 'Siemens', 'HB514FBR0T', 'Siemens built-in oven', 2200.00::NUMERIC, 3200.00::NUMERIC, 4000.00::NUMERIC, 'Siemens Turkiye', 'https://www.siemens-home.bsh-group.com/tr/', 'Catalog model profile; watt thresholds are WattSmart monitoring defaults.'),
        ('OVEN', 'Arcelik', 'AFM 340 I', 'Arcelik built-in oven', 2200.00::NUMERIC, 3200.00::NUMERIC, 4000.00::NUMERIC, 'Arcelik', 'https://www.arcelik.com.tr/ankastre-firin', 'Catalog model profile; watt thresholds are WattSmart monitoring defaults.'),
        ('COOKTOP', 'Bosch', 'PKE611D17E', 'Bosch electric ceramic cooktop', 1800.00::NUMERIC, 3000.00::NUMERIC, 7200.00::NUMERIC, 'Bosch Turkiye', 'https://www.bosch-home.com.tr/tr/category/ankastre-ocaklar', 'Catalog model profile; watt thresholds are WattSmart monitoring defaults.'),
        ('COOKTOP', 'Siemens', 'ET651HE17E', 'Siemens electric ceramic cooktop', 1800.00::NUMERIC, 3000.00::NUMERIC, 7200.00::NUMERIC, 'Siemens Turkiye', 'https://www.siemens-home.bsh-group.com/tr/', 'Catalog model profile; watt thresholds are WattSmart monitoring defaults.'),
        ('MICROWAVE', 'Samsung', 'MS23K3515AS/TR', 'Samsung MS23K3515AS solo microwave', 900.00::NUMERIC, 1400.00::NUMERIC, 1800.00::NUMERIC, 'Samsung Turkiye', 'https://www.samsung.com/tr/microwave-ovens/', 'Catalog model profile; watt thresholds are WattSmart monitoring defaults.'),
        ('MICROWAVE', 'Bosch', 'FEL023MS2', 'Bosch Serie 2 freestanding microwave', 900.00::NUMERIC, 1400.00::NUMERIC, 1800.00::NUMERIC, 'Bosch Turkiye', 'https://www.bosch-home.com.tr/tr/category/mikrodalga-firinlar', 'Catalog model profile; watt thresholds are WattSmart monitoring defaults.'),
        ('KETTLE', 'Arcelik', 'K 3283 IN', 'Arcelik stainless electric kettle', 2000.00::NUMERIC, 2400.00::NUMERIC, 3000.00::NUMERIC, 'Arcelik', 'https://www.arcelik.com.tr/kettle', 'Catalog model profile; watt thresholds are WattSmart monitoring defaults.'),
        ('KETTLE', 'Philips', 'HD9318/20', 'Philips Daily Collection kettle', 2000.00::NUMERIC, 2400.00::NUMERIC, 3000.00::NUMERIC, 'Philips', 'https://www.philips.com.tr/', 'Catalog model profile; watt thresholds are WattSmart monitoring defaults.'),
        ('COFFEE_MACHINE', 'Delonghi', 'Magnifica S ECAM 22.110.B', 'DeLonghi Magnifica S espresso machine', 1450.00::NUMERIC, 1800.00::NUMERIC, 2200.00::NUMERIC, 'DeLonghi', 'https://www.delonghi.com/', 'Catalog model profile; watt thresholds are WattSmart monitoring defaults.'),
        ('COFFEE_MACHINE', 'Philips', 'EP2220/10', 'Philips 2200 Series espresso machine', 1500.00::NUMERIC, 1800.00::NUMERIC, 2200.00::NUMERIC, 'Philips', 'https://www.philips.com.tr/', 'Catalog model profile; watt thresholds are WattSmart monitoring defaults.'),
        ('HOOD', 'Siemens', 'LC65KAJ60T', 'Siemens wall-mounted cooker hood 60 cm', 180.00::NUMERIC, 350.00::NUMERIC, 700.00::NUMERIC, 'Siemens Turkiye', 'https://www.siemens-home.bsh-group.com/tr/', 'Catalog model profile; watt thresholds are WattSmart monitoring defaults.'),
        ('HOOD', 'Arcelik', 'P 39 YEI', 'Arcelik built-in cooker hood', 180.00::NUMERIC, 350.00::NUMERIC, 700.00::NUMERIC, 'Arcelik', 'https://www.arcelik.com.tr/davlumbaz', 'Catalog model profile; watt thresholds are WattSmart monitoring defaults.'),
        ('AIR_CONDITIONER', 'Arcelik', '12325 A', 'Arcelik 12000 BTU inverter air conditioner', 1500.00::NUMERIC, 2600.00::NUMERIC, 3500.00::NUMERIC, 'Arcelik', 'https://www.arcelik.com.tr/klima', 'Catalog model profile; watt thresholds are WattSmart monitoring defaults.'),
        ('AIR_CONDITIONER', 'Daikin', 'Shira Eco FTXP35M', 'Daikin Shira Eco 12000 BTU air conditioner', 1500.00::NUMERIC, 2600.00::NUMERIC, 3500.00::NUMERIC, 'Daikin', 'https://www.daikin.com.tr/', 'Catalog model profile; watt thresholds are WattSmart monitoring defaults.'),
        ('AIR_CONDITIONER', 'Mitsubishi Electric', 'MSZ-HR35VF', 'Mitsubishi Electric MSZ-HR35VF air conditioner', 1500.00::NUMERIC, 2600.00::NUMERIC, 3500.00::NUMERIC, 'Mitsubishi Electric', 'https://tr.mitsubishielectric.com/', 'Catalog model profile; watt thresholds are WattSmart monitoring defaults.'),
        ('AIR_PURIFIER', 'Xiaomi', 'Smart Air Purifier 4', 'Xiaomi Smart Air Purifier 4', 45.00::NUMERIC, 120.00::NUMERIC, 180.00::NUMERIC, 'Xiaomi', 'https://www.mi.com/tr/', 'Catalog model profile; watt thresholds are WattSmart monitoring defaults.'),
        ('AIR_PURIFIER', 'Dyson', 'Purifier Cool Gen1 TP10', 'Dyson Purifier Cool Gen1 TP10', 50.00::NUMERIC, 120.00::NUMERIC, 180.00::NUMERIC, 'Dyson', 'https://www.dyson.com.tr/', 'Catalog model profile; watt thresholds are WattSmart monitoring defaults.'),
        ('AIR_PURIFIER', 'Philips', 'AC1715/10', 'Philips Air Purifier Series 1000i', 45.00::NUMERIC, 120.00::NUMERIC, 180.00::NUMERIC, 'Philips', 'https://www.philips.com.tr/', 'Catalog model profile; watt thresholds are WattSmart monitoring defaults.'),
        ('DEHUMIDIFIER', 'Arcelik', 'Nem Al 15', 'Arcelik dehumidifier 15 L', 350.00::NUMERIC, 650.00::NUMERIC, 900.00::NUMERIC, 'Arcelik', 'https://www.arcelik.com.tr/', 'Catalog model profile; watt thresholds are WattSmart monitoring defaults.'),
        ('DEHUMIDIFIER', 'Delonghi', 'Tasciugo AriaDry Light DNS65', 'DeLonghi Tasciugo AriaDry Light dehumidifier', 350.00::NUMERIC, 650.00::NUMERIC, 900.00::NUMERIC, 'DeLonghi', 'https://www.delonghi.com/', 'Catalog model profile; watt thresholds are WattSmart monitoring defaults.'),
        ('WATER_HEATER', 'Ariston', 'Velis Evo 80', 'Ariston Velis Evo 80 electric water heater', 1500.00::NUMERIC, 2500.00::NUMERIC, 3500.00::NUMERIC, 'Ariston', 'https://www.ariston.com/', 'Catalog model profile; watt thresholds are WattSmart monitoring defaults.'),
        ('WATER_HEATER', 'Bosch', 'Tronic 2000 T 80', 'Bosch Tronic 2000 T electric water heater', 1500.00::NUMERIC, 2500.00::NUMERIC, 3500.00::NUMERIC, 'Bosch Turkiye', 'https://www.bosch-homecomfort.com/tr/tr/', 'Catalog model profile; watt thresholds are WattSmart monitoring defaults.'),
        ('HEAT_PUMP', 'Daikin', 'Altherma 3 R', 'Daikin Altherma 3 R heat pump', 1800.00::NUMERIC, 3200.00::NUMERIC, 4200.00::NUMERIC, 'Daikin', 'https://www.daikin.com.tr/', 'Catalog model profile; watt thresholds are WattSmart monitoring defaults.'),
        ('HEAT_PUMP', 'Mitsubishi Electric', 'Ecodan', 'Mitsubishi Electric Ecodan heat pump', 1800.00::NUMERIC, 3200.00::NUMERIC, 4200.00::NUMERIC, 'Mitsubishi Electric', 'https://tr.mitsubishielectric.com/', 'Catalog model profile; watt thresholds are WattSmart monitoring defaults.'),
        ('SPACE_HEATER', 'Delonghi', 'Dragon 4 TRD4 1025', 'DeLonghi Dragon 4 oil-filled radiator', 1800.00::NUMERIC, 2500.00::NUMERIC, 3200.00::NUMERIC, 'DeLonghi', 'https://www.delonghi.com/', 'Catalog model profile; watt thresholds are WattSmart monitoring defaults.'),
        ('SPACE_HEATER', 'Ufo', 'Star S/19', 'Ufo Star infrared heater', 1900.00::NUMERIC, 2500.00::NUMERIC, 3200.00::NUMERIC, 'Ufo', 'https://www.ufo.com.tr/', 'Catalog model profile; watt thresholds are WattSmart monitoring defaults.'),
        ('EV_CHARGER', 'Tesla', 'Wall Connector Gen 3', 'Tesla Wall Connector Gen 3', 7400.00::NUMERIC, 7500.00::NUMERIC, 11000.00::NUMERIC, 'Tesla', 'https://www.tesla.com/', 'Catalog model profile; watt thresholds are WattSmart monitoring defaults.'),
        ('EV_CHARGER', 'Vestel', 'EVC04', 'Vestel EVC04 EV charger', 7400.00::NUMERIC, 7500.00::NUMERIC, 11000.00::NUMERIC, 'Vestel', 'https://www.vestel.com.tr/', 'Catalog model profile; watt thresholds are WattSmart monitoring defaults.'),
        ('LIGHTING', 'Philips Hue', 'White and Color Ambiance', 'Philips Hue smart lighting circuit', 300.00::NUMERIC, 800.00::NUMERIC, 1200.00::NUMERIC, 'Philips Hue', 'https://www.philips-hue.com/', 'Grouped lighting profile; watt thresholds are WattSmart monitoring defaults.'),
        ('LIGHTING', 'Viko', 'LED Lighting Circuit', 'Viko residential LED lighting circuit', 300.00::NUMERIC, 800.00::NUMERIC, 1200.00::NUMERIC, 'Viko', 'https://www.viko.com.tr/', 'Grouped lighting profile; watt thresholds are WattSmart monitoring defaults.'),
        ('TELEVISION', 'Samsung', 'QE55Q60D', 'Samsung Q60D 55 inch QLED TV', 120.00::NUMERIC, 300.00::NUMERIC, 500.00::NUMERIC, 'Samsung Turkiye', 'https://www.samsung.com/tr/tvs/', 'Catalog model profile; watt thresholds are WattSmart monitoring defaults.'),
        ('TELEVISION', 'LG', 'OLED55C4', 'LG OLED C4 55 inch TV', 140.00::NUMERIC, 320.00::NUMERIC, 520.00::NUMERIC, 'LG Turkiye', 'https://www.lg.com/tr/tv-ses-video/tv/', 'Catalog model profile; watt thresholds are WattSmart monitoring defaults.'),
        ('TELEVISION', 'Sony', 'XR-55X90L', 'Sony Bravia XR X90L 55 inch TV', 140.00::NUMERIC, 320.00::NUMERIC, 520.00::NUMERIC, 'Sony', 'https://www.sony.com.tr/', 'Catalog model profile; watt thresholds are WattSmart monitoring defaults.'),
        ('GAME_CONSOLE', 'Sony', 'PlayStation 5 Slim', 'Sony PlayStation 5 Slim', 200.00::NUMERIC, 350.00::NUMERIC, 500.00::NUMERIC, 'Sony', 'https://www.playstation.com/', 'Catalog model profile; watt thresholds are WattSmart monitoring defaults.'),
        ('GAME_CONSOLE', 'Microsoft', 'Xbox Series X', 'Microsoft Xbox Series X', 200.00::NUMERIC, 350.00::NUMERIC, 500.00::NUMERIC, 'Microsoft', 'https://www.xbox.com/', 'Catalog model profile; watt thresholds are WattSmart monitoring defaults.'),
        ('COMPUTER', 'Apple', 'Mac mini M2', 'Apple Mac mini M2 workstation', 80.00::NUMERIC, 250.00::NUMERIC, 400.00::NUMERIC, 'Apple', 'https://www.apple.com/', 'Catalog model profile; watt thresholds are WattSmart monitoring defaults.'),
        ('COMPUTER', 'Lenovo', 'ThinkStation P3 Tiny', 'Lenovo ThinkStation P3 Tiny workstation', 180.00::NUMERIC, 500.00::NUMERIC, 800.00::NUMERIC, 'Lenovo', 'https://www.lenovo.com/', 'Catalog model profile; watt thresholds are WattSmart monitoring defaults.'),
        ('COMPUTER', 'Asus', 'ROG Gaming Desktop', 'Asus ROG gaming desktop', 450.00::NUMERIC, 800.00::NUMERIC, 1200.00::NUMERIC, 'Asus', 'https://www.asus.com/tr/', 'Catalog model profile; watt thresholds are WattSmart monitoring defaults.'),
        ('LAPTOP', 'Apple', 'MacBook Pro 14 M3', 'Apple MacBook Pro 14 inch M3', 70.00::NUMERIC, 140.00::NUMERIC, 240.00::NUMERIC, 'Apple', 'https://www.apple.com/', 'Catalog model profile; watt thresholds are WattSmart monitoring defaults.'),
        ('LAPTOP', 'Lenovo', 'ThinkPad X1 Carbon Gen 12', 'Lenovo ThinkPad X1 Carbon Gen 12', 65.00::NUMERIC, 140.00::NUMERIC, 240.00::NUMERIC, 'Lenovo', 'https://www.lenovo.com/', 'Catalog model profile; watt thresholds are WattSmart monitoring defaults.'),
        ('LAPTOP', 'Asus', 'Zenbook 14 OLED', 'Asus Zenbook 14 OLED', 65.00::NUMERIC, 140.00::NUMERIC, 240.00::NUMERIC, 'Asus', 'https://www.asus.com/tr/', 'Catalog model profile; watt thresholds are WattSmart monitoring defaults.'),
        ('ROUTER', 'TP-Link', 'Archer AX55', 'TP-Link Archer AX55 Wi-Fi 6 router', 15.00::NUMERIC, 50.00::NUMERIC, 80.00::NUMERIC, 'TP-Link', 'https://www.tp-link.com/tr/', 'Catalog model profile; watt thresholds are WattSmart monitoring defaults.'),
        ('ROUTER', 'Asus', 'RT-AX58U', 'Asus RT-AX58U Wi-Fi 6 router', 18.00::NUMERIC, 50.00::NUMERIC, 80.00::NUMERIC, 'Asus', 'https://www.asus.com/tr/networking-iot-servers/', 'Catalog model profile; watt thresholds are WattSmart monitoring defaults.'),
        ('ROUTER', 'Xiaomi', 'Router AX3200', 'Xiaomi Router AX3200', 15.00::NUMERIC, 50.00::NUMERIC, 80.00::NUMERIC, 'Xiaomi', 'https://www.mi.com/tr/', 'Catalog model profile; watt thresholds are WattSmart monitoring defaults.'),
        ('VACUUM_CLEANER', 'Dyson', 'V15 Detect Absolute', 'Dyson V15 Detect Absolute vacuum', 660.00::NUMERIC, 1000.00::NUMERIC, 1400.00::NUMERIC, 'Dyson', 'https://www.dyson.com.tr/', 'Catalog model profile; watt thresholds are WattSmart monitoring defaults.'),
        ('VACUUM_CLEANER', 'Philips', 'FC9749/07', 'Philips PowerPro Expert vacuum cleaner', 900.00::NUMERIC, 1400.00::NUMERIC, 2000.00::NUMERIC, 'Philips', 'https://www.philips.com.tr/', 'Catalog model profile; watt thresholds are WattSmart monitoring defaults.'),
        ('VACUUM_CLEANER', 'Bosch', 'BGS41POW2', 'Bosch ProPower vacuum cleaner', 900.00::NUMERIC, 1400.00::NUMERIC, 2000.00::NUMERIC, 'Bosch Turkiye', 'https://www.bosch-home.com.tr/tr/category/elektrikli-supurgeler', 'Catalog model profile; watt thresholds are WattSmart monitoring defaults.'),
        ('ROBOT_VACUUM', 'Roborock', 'S8 MaxV Ultra', 'Roborock S8 MaxV Ultra robot vacuum', 60.00::NUMERIC, 140.00::NUMERIC, 220.00::NUMERIC, 'Roborock', 'https://global.roborock.com/', 'Catalog model profile; watt thresholds are WattSmart monitoring defaults.'),
        ('ROBOT_VACUUM', 'Xiaomi', 'Robot Vacuum X20+', 'Xiaomi Robot Vacuum X20+', 60.00::NUMERIC, 140.00::NUMERIC, 220.00::NUMERIC, 'Xiaomi', 'https://www.mi.com/tr/', 'Catalog model profile; watt thresholds are WattSmart monitoring defaults.'),
        ('ROBOT_VACUUM', 'iRobot', 'Roomba j7+', 'iRobot Roomba j7+ robot vacuum', 60.00::NUMERIC, 140.00::NUMERIC, 220.00::NUMERIC, 'iRobot', 'https://www.irobot.com/', 'Catalog model profile; watt thresholds are WattSmart monitoring defaults.'),
        ('IRON', 'Philips', 'DST8050/20 Azur 8000', 'Philips Azur 8000 steam iron', 2400.00::NUMERIC, 2800.00::NUMERIC, 3200.00::NUMERIC, 'Philips', 'https://www.philips.com.tr/', 'Catalog model profile; watt thresholds are WattSmart monitoring defaults.'),
        ('IRON', 'Tefal', 'FV9845 Ultimate Pure', 'Tefal Ultimate Pure steam iron', 2400.00::NUMERIC, 2800.00::NUMERIC, 3200.00::NUMERIC, 'Tefal', 'https://www.tefal.com.tr/', 'Catalog model profile; watt thresholds are WattSmart monitoring defaults.')
)
INSERT INTO appliance_model_profiles (
    appliance_type_id,
    manufacturer,
    model_name,
    display_name,
    nominal_wattage,
    safe_watt_limit,
    peak_watt_limit,
    source_name,
    source_url,
    notes
)
SELECT
    appliance_types.id,
    appliance_model_seed.manufacturer,
    appliance_model_seed.model_name,
    appliance_model_seed.display_name,
    appliance_model_seed.nominal_wattage,
    appliance_model_seed.safe_watt_limit,
    appliance_model_seed.peak_watt_limit,
    appliance_model_seed.source_name,
    appliance_model_seed.source_url,
    appliance_model_seed.notes
FROM appliance_model_seed
JOIN appliance_types
    ON appliance_types.code = appliance_model_seed.type_code
ON CONFLICT (appliance_type_id, manufacturer, model_name) DO UPDATE
SET display_name = EXCLUDED.display_name,
    nominal_wattage = EXCLUDED.nominal_wattage,
    safe_watt_limit = EXCLUDED.safe_watt_limit,
    peak_watt_limit = EXCLUDED.peak_watt_limit,
    source_name = EXCLUDED.source_name,
    source_url = EXCLUDED.source_url,
    notes = EXCLUDED.notes,
    updated_at = NOW();

WITH seed_users (
    email,
    first_name,
    last_name,
    roles
) AS (
    VALUES
        ('demo.admin@wattsmart.local', 'Demo', 'Admin', ARRAY['ADMIN'::user_role]),
        ('demo.operator@wattsmart.local', 'Demo', 'Operator', ARRAY['OPERATOR'::user_role]),
        ('ayse.yilmaz@wattsmart.local', 'Ayse', 'Yilmaz', ARRAY['RESIDENT'::user_role]),
        ('mehmet.kaya@wattsmart.local', 'Mehmet', 'Kaya', ARRAY['RESIDENT'::user_role]),
        ('zeynep.demir@wattsmart.local', 'Zeynep', 'Demir', ARRAY['RESIDENT'::user_role])
),
upserted_users AS (
    INSERT INTO users (
        email,
        password_hash,
        first_name,
        last_name,
        auth_provider,
        email_verified_at,
        status
    )
    SELECT
        email,
        -- Local demo password: password
        '$2a$10$GGwEFCxrl7esACtIfbDp7eo1c0TpEStT1hpXstQw4/mWVrdgxElrG',
        first_name,
        last_name,
        'LOCAL'::auth_provider,
        NOW(),
        'ACTIVE'::user_status
    FROM seed_users
    ON CONFLICT (email) DO UPDATE
    SET first_name = EXCLUDED.first_name,
        last_name = EXCLUDED.last_name,
        password_hash = EXCLUDED.password_hash,
        auth_provider = EXCLUDED.auth_provider,
        status = EXCLUDED.status,
        email_verified_at = COALESCE(users.email_verified_at, EXCLUDED.email_verified_at),
        updated_at = NOW()
    RETURNING id, email
)
INSERT INTO user_role_assignments (
    user_id,
    role
)
SELECT
    upserted_users.id,
    role_item.role
FROM upserted_users
JOIN seed_users
    ON seed_users.email = upserted_users.email
CROSS JOIN LATERAL UNNEST(seed_users.roles) AS role_item(role)
ON CONFLICT (user_id, role) DO NOTHING;

INSERT INTO user_notification_preferences (
    user_id,
    email_enabled,
    usage_milestone_enabled,
    anomaly_alert_enabled,
    monthly_summary_enabled
)
SELECT
    users.id,
    TRUE,
    TRUE,
    TRUE,
    TRUE
FROM users
WHERE users.email IN (
    'demo.admin@wattsmart.local',
    'demo.operator@wattsmart.local',
    'ayse.yilmaz@wattsmart.local',
    'mehmet.kaya@wattsmart.local',
    'zeynep.demir@wattsmart.local'
)
ON CONFLICT (user_id) DO UPDATE
SET email_enabled = EXCLUDED.email_enabled,
    usage_milestone_enabled = EXCLUDED.usage_milestone_enabled,
    anomaly_alert_enabled = EXCLUDED.anomaly_alert_enabled,
    monthly_summary_enabled = EXCLUDED.monthly_summary_enabled,
    updated_at = NOW();

WITH seed_homes (
    external_key,
    name,
    address_line_1,
    city,
    region,
    postal_code,
    country_code,
    timezone_name,
    resident_email,
    tariff_code,
    monthly_usage_limit_kwh,
    billing_cycle_start_day
) AS (
    VALUES
        ('DEMO-HOME-KADIKOY-001', 'Kadikoy Family Flat', 'Moda Caddesi No: 24 D: 8', 'Istanbul', 'Kadikoy', '34710', 'TR', 'Europe/Istanbul', 'ayse.yilmaz@wattsmart.local', 'RESIDENTIAL_STANDARD_TR', 280.000::NUMERIC, 1::SMALLINT),
        ('DEMO-HOME-BESIKTAS-002', 'Besiktas Smart Apartment', 'Sinanpasa Mahallesi No: 18', 'Istanbul', 'Besiktas', '34353', 'TR', 'Europe/Istanbul', 'ayse.yilmaz@wattsmart.local', 'RESIDENTIAL_ECO_TR', 220.000::NUMERIC, 1::SMALLINT),
        ('DEMO-HOME-CANKAYA-003', 'Cankaya Villa', 'Tunali Hilmi Caddesi No: 75', 'Ankara', 'Cankaya', '06680', 'TR', 'Europe/Istanbul', 'mehmet.kaya@wattsmart.local', 'RESIDENTIAL_PEAK_TR', 420.000::NUMERIC, 1::SMALLINT),
        ('DEMO-HOME-BORNOVA-004', 'Bornova Student House', 'Kazim Dirik Mahallesi No: 11', 'Izmir', 'Bornova', '35100', 'TR', 'Europe/Istanbul', 'zeynep.demir@wattsmart.local', 'RESIDENTIAL_ECO_TR', 180.000::NUMERIC, 1::SMALLINT),
        ('DEMO-HOME-NILUFER-005', 'Nilufer Townhouse', 'Ozluce Mahallesi No: 41', 'Bursa', 'Nilufer', '16120', 'TR', 'Europe/Istanbul', 'mehmet.kaya@wattsmart.local', 'RESIDENTIAL_STANDARD_TR', 320.000::NUMERIC, 1::SMALLINT),
        ('DEMO-HOME-MURATPASA-006', 'Muratpasa Summer Home', 'Lara Caddesi No: 9', 'Antalya', 'Muratpasa', '07160', 'TR', 'Europe/Istanbul', 'zeynep.demir@wattsmart.local', 'RESIDENTIAL_PEAK_TR', 360.000::NUMERIC, 1::SMALLINT)
),
upserted_homes AS (
    INSERT INTO homes (
        external_key,
        name,
        status,
        address_line_1,
        city,
        region,
        postal_code,
        country_code,
        timezone_name
    )
    SELECT
        external_key,
        name,
        'ACTIVE'::home_status,
        address_line_1,
        city,
        region,
        postal_code,
        country_code,
        timezone_name
    FROM seed_homes
    ON CONFLICT (external_key) DO UPDATE
    SET name = EXCLUDED.name,
        status = EXCLUDED.status,
        address_line_1 = EXCLUDED.address_line_1,
        city = EXCLUDED.city,
        region = EXCLUDED.region,
        postal_code = EXCLUDED.postal_code,
        country_code = EXCLUDED.country_code,
        timezone_name = EXCLUDED.timezone_name,
        updated_at = NOW()
    RETURNING id, external_key
)
INSERT INTO home_user_memberships (
    home_id,
    user_id,
    accepted_at
)
SELECT
    upserted_homes.id,
    users.id,
    NOW()
FROM upserted_homes
JOIN seed_homes
    ON seed_homes.external_key = upserted_homes.external_key
JOIN users
    ON users.email = seed_homes.resident_email
ON CONFLICT (home_id, user_id) DO UPDATE
SET accepted_at = COALESCE(home_user_memberships.accepted_at, EXCLUDED.accepted_at),
    updated_at = NOW();

WITH seed_homes (
    external_key,
    tariff_code,
    monthly_usage_limit_kwh,
    billing_cycle_start_day
) AS (
    VALUES
        ('DEMO-HOME-KADIKOY-001', 'RESIDENTIAL_STANDARD_TR', 280.000::NUMERIC, 1::SMALLINT),
        ('DEMO-HOME-BESIKTAS-002', 'RESIDENTIAL_ECO_TR', 220.000::NUMERIC, 1::SMALLINT),
        ('DEMO-HOME-CANKAYA-003', 'RESIDENTIAL_PEAK_TR', 420.000::NUMERIC, 1::SMALLINT),
        ('DEMO-HOME-BORNOVA-004', 'RESIDENTIAL_ECO_TR', 180.000::NUMERIC, 1::SMALLINT),
        ('DEMO-HOME-NILUFER-005', 'RESIDENTIAL_STANDARD_TR', 320.000::NUMERIC, 1::SMALLINT),
        ('DEMO-HOME-MURATPASA-006', 'RESIDENTIAL_PEAK_TR', 360.000::NUMERIC, 1::SMALLINT)
)
INSERT INTO home_tariff_plans (
    home_id,
    tariff_plan_id,
    monthly_usage_limit_kwh,
    billing_cycle_start_day,
    effective_from
)
SELECT
    homes.id,
    tariff_plans.id,
    seed_homes.monthly_usage_limit_kwh,
    seed_homes.billing_cycle_start_day,
    DATE '2026-01-01'
FROM seed_homes
JOIN homes
    ON homes.external_key = seed_homes.external_key
JOIN tariff_plans
    ON tariff_plans.code = seed_homes.tariff_code
ON CONFLICT (home_id, effective_from) DO UPDATE
SET tariff_plan_id = EXCLUDED.tariff_plan_id,
    monthly_usage_limit_kwh = EXCLUDED.monthly_usage_limit_kwh,
    billing_cycle_start_day = EXCLUDED.billing_cycle_start_day,
    updated_at = NOW();

INSERT INTO home_billing_accounts (
    home_id,
    current_cycle_started_on,
    current_cycle_ends_on,
    current_cycle_usage_kwh,
    current_cycle_base_cost_amount,
    current_cycle_penalty_cost_amount,
    total_cost_amount
)
SELECT
    homes.id,
    DATE_TRUNC('month', CURRENT_DATE)::DATE,
    (DATE_TRUNC('month', CURRENT_DATE)::DATE + INTERVAL '1 month - 1 day')::DATE,
    0.000::NUMERIC,
    0.00::NUMERIC,
    0.00::NUMERIC,
    0.00::NUMERIC
FROM homes
WHERE homes.external_key LIKE 'DEMO-HOME-%'
ON CONFLICT (home_id) DO UPDATE
SET current_cycle_started_on = EXCLUDED.current_cycle_started_on,
    current_cycle_ends_on = EXCLUDED.current_cycle_ends_on,
    updated_at = NOW();

WITH seed_home_appliances (
    home_external_key,
    appliance_code,
    type_code,
    manufacturer,
    model_name,
    display_name,
    display_order
) AS (
    VALUES
        ('DEMO-HOME-KADIKOY-001', 'kitchen-fridge', 'REFRIGERATOR', 'Samsung', 'RM90F66CEW/TR', 'Kitchen refrigerator', 1::SMALLINT),
        ('DEMO-HOME-KADIKOY-001', 'dishwasher', 'DISHWASHER', 'Bosch', 'SMS6ECI83T', 'Dishwasher', 2::SMALLINT),
        ('DEMO-HOME-KADIKOY-001', 'living-room-ac', 'AIR_CONDITIONER', 'Daikin', 'Shira Eco FTXP35M', 'Living room AC', 3::SMALLINT),
        ('DEMO-HOME-KADIKOY-001', 'router', 'ROUTER', 'TP-Link', 'Archer AX55', 'Network router', 4::SMALLINT),
        ('DEMO-HOME-BESIKTAS-002', 'air-purifier', 'AIR_PURIFIER', 'Xiaomi', 'Smart Air Purifier 4', 'Air purifier', 1::SMALLINT),
        ('DEMO-HOME-BESIKTAS-002', 'washing-machine', 'WASHING_MACHINE', 'Samsung', 'WW90T4020CE/AH', 'Washing machine', 2::SMALLINT),
        ('DEMO-HOME-BESIKTAS-002', 'tv', 'TELEVISION', 'LG', 'OLED55C4', 'Living room TV', 3::SMALLINT),
        ('DEMO-HOME-BESIKTAS-002', 'robot-vacuum', 'ROBOT_VACUUM', 'Roborock', 'S8 MaxV Ultra', 'Robot vacuum', 4::SMALLINT),
        ('DEMO-HOME-CANKAYA-003', 'heat-pump', 'HEAT_PUMP', 'Daikin', 'Altherma 3 R', 'Main heat pump', 1::SMALLINT),
        ('DEMO-HOME-CANKAYA-003', 'ev-charger', 'EV_CHARGER', 'Tesla', 'Wall Connector Gen 3', 'Garage EV charger', 2::SMALLINT),
        ('DEMO-HOME-CANKAYA-003', 'oven', 'OVEN', 'Bosch', 'HBF534EB0T', 'Built-in oven', 3::SMALLINT),
        ('DEMO-HOME-CANKAYA-003', 'cooktop', 'COOKTOP', 'Bosch', 'PKE611D17E', 'Cooktop', 4::SMALLINT),
        ('DEMO-HOME-CANKAYA-003', 'dryer', 'DRYER', 'Bosch', 'WQG241A0TR', 'Dryer', 5::SMALLINT),
        ('DEMO-HOME-BORNOVA-004', 'mini-fridge', 'REFRIGERATOR', 'Beko', '970475 MB', 'Shared refrigerator', 1::SMALLINT),
        ('DEMO-HOME-BORNOVA-004', 'gaming-desktop', 'COMPUTER', 'Asus', 'ROG Gaming Desktop', 'Gaming desktop', 2::SMALLINT),
        ('DEMO-HOME-BORNOVA-004', 'game-console', 'GAME_CONSOLE', 'Sony', 'PlayStation 5 Slim', 'Game console', 3::SMALLINT),
        ('DEMO-HOME-BORNOVA-004', 'kettle', 'KETTLE', 'Philips', 'HD9318/20', 'Electric kettle', 4::SMALLINT),
        ('DEMO-HOME-NILUFER-005', 'fridge', 'REFRIGERATOR', 'Arcelik', '270531 EI', 'Kitchen refrigerator', 1::SMALLINT),
        ('DEMO-HOME-NILUFER-005', 'washer', 'WASHING_MACHINE', 'Arcelik', '10120 M', 'Washing machine', 2::SMALLINT),
        ('DEMO-HOME-NILUFER-005', 'water-heater', 'WATER_HEATER', 'Ariston', 'Velis Evo 80', 'Water heater', 3::SMALLINT),
        ('DEMO-HOME-NILUFER-005', 'hood', 'HOOD', 'Arcelik', 'P 39 YEI', 'Cooker hood', 4::SMALLINT),
        ('DEMO-HOME-MURATPASA-006', 'bedroom-ac', 'AIR_CONDITIONER', 'Arcelik', '12325 A', 'Bedroom AC', 1::SMALLINT),
        ('DEMO-HOME-MURATPASA-006', 'dehumidifier', 'DEHUMIDIFIER', 'Delonghi', 'Tasciugo AriaDry Light DNS65', 'Dehumidifier', 2::SMALLINT),
        ('DEMO-HOME-MURATPASA-006', 'freezer', 'FREEZER', 'Ugur', 'UED 7266 DTK', 'Freezer', 3::SMALLINT),
        ('DEMO-HOME-MURATPASA-006', 'coffee-machine', 'COFFEE_MACHINE', 'Delonghi', 'Magnifica S ECAM 22.110.B', 'Coffee machine', 4::SMALLINT)
)
INSERT INTO appliances (
    home_id,
    appliance_type_id,
    appliance_code,
    name,
    manufacturer,
    model_name,
    nominal_wattage,
    safe_watt_limit,
    display_order,
    is_active,
    installed_at
)
SELECT
    homes.id,
    appliance_types.id,
    seed_home_appliances.appliance_code,
    seed_home_appliances.display_name,
    seed_home_appliances.manufacturer,
    seed_home_appliances.model_name,
    appliance_model_profiles.nominal_wattage,
    COALESCE(appliance_model_profiles.safe_watt_limit, appliance_types.default_safe_watt_limit),
    seed_home_appliances.display_order,
    TRUE,
    NOW()
FROM seed_home_appliances
JOIN homes
    ON homes.external_key = seed_home_appliances.home_external_key
JOIN appliance_types
    ON appliance_types.code = seed_home_appliances.type_code
LEFT JOIN appliance_model_profiles
    ON appliance_model_profiles.appliance_type_id = appliance_types.id
    AND appliance_model_profiles.manufacturer = seed_home_appliances.manufacturer
    AND appliance_model_profiles.model_name = seed_home_appliances.model_name
ON CONFLICT (home_id, appliance_code) DO UPDATE
SET appliance_type_id = EXCLUDED.appliance_type_id,
    name = EXCLUDED.name,
    manufacturer = EXCLUDED.manufacturer,
    model_name = EXCLUDED.model_name,
    nominal_wattage = EXCLUDED.nominal_wattage,
    safe_watt_limit = EXCLUDED.safe_watt_limit,
    display_order = EXCLUDED.display_order,
    is_active = EXCLUDED.is_active,
    updated_at = NOW();

WITH scheduled_job_seed (
    job_key,
    name,
    description,
    fixed_interval_seconds,
    handler_name
) AS (
    VALUES
        ('rollup-appliance-usage-daily', 'Roll up appliance usage daily', 'Aggregates 30-minute appliance readings into daily appliance usage.', 86400, 'wattsmart.rollup_appliance_usage_daily'),
        ('rollup-home-usage-daily', 'Roll up home usage daily', 'Aggregates appliance daily usage into home daily usage.', 86400, 'wattsmart.rollup_home_usage_daily'),
        ('refresh-home-billing-accounts', 'Refresh home billing accounts', 'Refreshes current-cycle billing account totals from durable daily usage.', 3600, 'wattsmart.refresh_home_billing_accounts'),
        ('finalize-home-billing-cycles', 'Finalize home billing cycles', 'Snapshots ended billing cycles, rolls accounts into the next cycle, and resets live cycle totals.', 3600, 'wattsmart.finalize_home_billing_cycles'),
        ('rollup-home-usage-monthly', 'Roll up home usage monthly', 'Aggregates daily home usage into monthly prompt/reporting summaries.', 86400, 'wattsmart.rollup_home_usage_monthly'),
        ('generate-home-llm-monthly-summaries', 'Generate home LLM monthly summaries', 'Builds prompt-ready monthly home summary records.', 86400, 'wattsmart.generate_home_llm_monthly_summaries'),
        ('generate-monthly-llm-recommendations', 'Generate monthly LLM recommendations', 'Generates Turkish monthly resident recommendations and queues emails.', 86400, 'java.llm.generate_monthly_recommendations'),
        ('generate-urgent-llm-recommendations', 'Generate urgent LLM recommendations', 'Generates Turkish milestone/anomaly resident recommendations and queues emails.', 60, 'java.llm.generate_urgent_recommendations'),
        ('dispatch-email-notifications', 'Dispatch email notifications', 'Sends pending resident email notifications and records delivery attempts.', 60, 'java.email.dispatch_pending_notifications')
)
INSERT INTO scheduled_jobs (
    job_key,
    name,
    description,
    fixed_interval_seconds,
    handler_name,
    next_run_at
)
SELECT
    job_key,
    name,
    description,
    fixed_interval_seconds,
    handler_name,
    NOW()
FROM scheduled_job_seed
ON CONFLICT (job_key) DO UPDATE
SET name = EXCLUDED.name,
    description = EXCLUDED.description,
    fixed_interval_seconds = EXCLUDED.fixed_interval_seconds,
    handler_name = EXCLUDED.handler_name,
    next_run_at = COALESCE(scheduled_jobs.next_run_at, NOW()),
    updated_at = NOW();

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const jsonPath = path.resolve(
  __dirname,
  '../../web-app/mifos-web-next/apps/web/public/data/sectors.json'
);
const json = JSON.parse(fs.readFileSync(jsonPath, 'utf8'));

let sectorSeq = 1;
let industrySeq = 1;
let subIndustrySeq = 1;
const sectors = [];
const industries = [];
const subIndustries = [];
const lookup = [];

for (const [sectorName, indMap] of Object.entries(json)) {
  const sectorCode = `S${String(sectorSeq).padStart(3, '0')}`;
  sectors.push({ code: sectorCode, name: sectorName, id: sectorSeq });
  for (const [indName, subs] of Object.entries(indMap)) {
    const industryCode = `I${String(industrySeq).padStart(3, '0')}`;
    industries.push({ code: industryCode, name: indName, sectorId: sectorSeq, id: industrySeq });
    for (const subName of subs) {
      const subCode = `SI${String(subIndustrySeq).padStart(3, '0')}`;
      subIndustries.push({ code: subCode, name: subName, industryId: industrySeq, id: subIndustrySeq });
      lookup.push({
        subIndustryId: subIndustrySeq,
        sectorName,
        industryName: indName,
        subIndustryName: subName,
        subIndustryCode: subCode
      });
      subIndustrySeq += 1;
    }
    industrySeq += 1;
  }
  sectorSeq += 1;
}

const esc = (value) => value.replace(/'/g, "''");

function buildMysqlSeed() {
  let sql = '-- Auto-generated from sectors.json (MySQL / MariaDB)\n';
  sql += 'DELETE FROM m_sub_industry;\nDELETE FROM m_industry;\nDELETE FROM m_sector;\n\n';
  for (const sector of sectors) {
    sql += `INSERT INTO m_sector (id, sector_code, sector_name, created_by, created_on_utc, last_modified_by, last_modified_on_utc) VALUES (${sector.id}, '${sector.code}', '${esc(sector.name)}', 1, UTC_TIMESTAMP(6), 1, UTC_TIMESTAMP(6));\n`;
  }
  sql += '\n';
  for (const industry of industries) {
    sql += `INSERT INTO m_industry (id, industry_code, industry_name, sector_id, created_by, created_on_utc, last_modified_by, last_modified_on_utc) VALUES (${industry.id}, '${industry.code}', '${esc(industry.name)}', ${industry.sectorId}, 1, UTC_TIMESTAMP(6), 1, UTC_TIMESTAMP(6));\n`;
  }
  sql += '\n';
  for (const subIndustry of subIndustries) {
    sql += `INSERT INTO m_sub_industry (id, sub_industry_code, sub_industry_name, industry_id, created_by, created_on_utc, last_modified_by, last_modified_on_utc) VALUES (${subIndustry.id}, '${subIndustry.code}', '${esc(subIndustry.name)}', ${subIndustry.industryId}, 1, UTC_TIMESTAMP(6), 1, UTC_TIMESTAMP(6));\n`;
  }
  return sql;
}

function buildPostgresqlSeed() {
  let sql = '-- Auto-generated from sectors.json (PostgreSQL)\n';
  sql += 'DELETE FROM m_sub_industry;\nDELETE FROM m_industry;\nDELETE FROM m_sector;\n\n';
  for (const sector of sectors) {
    sql += `INSERT INTO m_sector (id, sector_code, sector_name, created_by, created_on_utc, last_modified_by, last_modified_on_utc) OVERRIDING SYSTEM VALUE VALUES (${sector.id}, '${sector.code}', '${esc(sector.name)}', 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP);\n`;
  }
  sql += '\n';
  for (const industry of industries) {
    sql += `INSERT INTO m_industry (id, industry_code, industry_name, sector_id, created_by, created_on_utc, last_modified_by, last_modified_on_utc) OVERRIDING SYSTEM VALUE VALUES (${industry.id}, '${industry.code}', '${esc(industry.name)}', ${industry.sectorId}, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP);\n`;
  }
  sql += '\n';
  for (const subIndustry of subIndustries) {
    sql += `INSERT INTO m_sub_industry (id, sub_industry_code, sub_industry_name, industry_id, created_by, created_on_utc, last_modified_by, last_modified_on_utc) OVERRIDING SYSTEM VALUE VALUES (${subIndustry.id}, '${subIndustry.code}', '${esc(subIndustry.name)}', ${subIndustry.industryId}, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP);\n`;
  }
  sql += '\n';
  sql += `-- Keep identity columns aligned with explicit seed ids (next id = max + 1).\n`;
  sql += `ALTER TABLE m_sector ALTER COLUMN id RESTART WITH ${sectorSeq};\n`;
  sql += `ALTER TABLE m_industry ALTER COLUMN id RESTART WITH ${industrySeq};\n`;
  sql += `ALTER TABLE m_sub_industry ALTER COLUMN id RESTART WITH ${subIndustrySeq};\n`;
  return sql;
}

const partsDir = path.resolve(
  __dirname,
  '../fineract-provider/src/main/resources/db/changelog/tenant/module/micropay/parts'
);

fs.writeFileSync(path.join(partsDir, 'V3016__seed_sectors_from_json.mysql.sql'), buildMysqlSeed());
fs.writeFileSync(path.join(partsDir, 'V3016__seed_sectors_from_json.sql'), buildPostgresqlSeed());

const lookupOut = path.resolve(
  __dirname,
  '../../web-app/mifos-web-next/apps/web/public/data/sectors-lookup.json'
);
fs.writeFileSync(lookupOut, `${JSON.stringify(lookup, null, 2)}\n`);

console.log(
  `Generated ${sectors.length} sectors, ${industries.length} industries, ${subIndustries.length} sub-industries (MySQL + PostgreSQL)`
);

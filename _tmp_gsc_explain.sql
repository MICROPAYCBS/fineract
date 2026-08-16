EXPLAIN
select x.* from (SELECT    x.* FROM      m_office o,
          m_group g,
          (
                    SELECT    a.activeclients,
                              (b.activeclientloans + c.activegrouploans) AS activeloans,
                              b.activeclientloans,
                              c.activegrouploans,
                              (b.activeclientborrowers + c.activegroupborrowers) AS activeborrowers,
                              b.activeclientborrowers,
                              c.activegroupborrowers,
                              (b.overdueclientloans + c.overduegrouploans) AS overdueloans,
                              b.overdueclientloans,
                              c.overduegrouploans
                    FROM      (
                                     SELECT Count(*) AS activeclients
                                     FROM   m_group topgroup
                                     JOIN   m_group g
                                     ON     g.hierarchy LIKE Concat(topgroup.hierarchy, '%')
                                     JOIN   m_group_client gc
                                     ON     gc.group_id = g.id
                                     JOIN   m_client c
                                     ON     c.id = gc.client_id
                                     WHERE  topgroup.id = 1
                                     AND    c.status_enum = 300) a,
                              (
                                     SELECT count(*) AS activeclientloans,
                                            count(DISTINCT(l.client_id)) AS activeclientborrowers,
                                            coalesce(sum(
                                            CASE
                                                   WHEN laa.loan_id IS NOT NULL THEN 1
                                                   ELSE 0
                                            end),
                                            0) AS overdueclientloans
                    FROM      m_group topgroup
                    JOIN      m_group g
                    ON        g.hierarchy LIKE concat(topgroup.hierarchy, '%')
                    JOIN      m_loan l
                    ON        l.group_id = g.id
                    AND       l.client_id IS NOT NULL
                    LEFT JOIN m_loan_arrears_aging laa
                    ON        laa.loan_id = l.id
                    WHERE     topgroup.id = 1
                    AND       l.loan_status_id = 300) b,
          (
                 SELECT count(*)  AS activegrouploans,
                        count(DISTINCT(l.group_id)) AS activegroupborrowers,
                        coalesce(sum(
                        CASE
                               WHEN laa.loan_id IS NOT NULL THEN 1
                               ELSE 0
                        end),
                        0) AS overduegrouploans
FROM      m_group topgroup JOIN      m_group g ON        g.hierarchy LIKE concat(topgroup.hierarchy, '%') JOIN      m_loan l ON        l.group_id = g.id AND       l.client_id IS NULL LEFT JOIN m_loan_arrears_aging laa ON        laa.loan_id = l.id WHERE     topgroup.id = 1 AND       l.loan_status_id = 300) c ) x WHERE g.id = 1 AND o.id = g.office_id AND o.hierarchy LIKE concat('.', '%')
) x;

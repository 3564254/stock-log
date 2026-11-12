-- stock_news: 기사 ID 고유 제약을 (provider, news_id) 복합 고유로 바꾼다 (MySQL)
-- ddl-auto: update 는 새 복합 제약은 만들지만, 예전 news_id 단독 고유 인덱스는 지우지 않는다.
-- 서버를 새 코드로 한 번 띄운 뒤(복합 제약 생성) 아래를 한 번 실행한다.

-- 1) news_id 하나만 걸린 고유 인덱스 이름 확인
SELECT INDEX_NAME
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'stock_news' AND NON_UNIQUE = 0
GROUP BY INDEX_NAME
HAVING COUNT(*) = 1 AND MAX(COLUMN_NAME) = 'news_id';

-- 2) 위에서 나온 이름으로 삭제 (예: UK_xxxxxxxx)
-- ALTER TABLE stock_news DROP INDEX UK_xxxxxxxx;

-- 3) 확인: uk_stock_news_provider_news_id 가 있고, news_id 단독 고유 인덱스는 없어야 한다
-- SHOW INDEX FROM stock_news;

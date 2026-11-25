-- Hash SHA-256 (hex) do token de gerenciamento. Nulo apenas em links criados antes desta migration,
-- que ficam sem dono e, portanto, não podem ser desativados nem consultados pela API.
ALTER TABLE short_url ADD COLUMN management_token_hash VARCHAR(64) NULL;

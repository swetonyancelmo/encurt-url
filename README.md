<div align="center">

# 🔗 Encurt URL

**Transforme links enormes em endereços curtos, e saiba quantas pessoas clicaram.**

![Java](https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.2-6DB33F?logo=springboot&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-17-4169E1?logo=postgresql&logoColor=white)
![Testes](https://img.shields.io/badge/testes-136_passando-brightgreen)

</div>

---

## ✨ O que ele faz

- ✂️ **Encurta** qualquer URL `http` ou `https`
- ↪️ **Redireciona** quem acessar o link curto
- 📊 **Conta os cliques**, com total e gráfico diário
- ⏳ **Expira links** numa data que você escolher
- 🔐 **Protege o gerenciamento** com um token, sem precisar de login
- 🕵️ **Respeita a privacidade:** o IP de quem clica nunca é guardado

## 🚀 Começando

Você precisa de **Java 25**, **Maven** e **Docker**.

```bash
docker compose up -d        # sobe o banco de dados
mvn spring-boot:run         # sobe a API
```

Pronto! Abra a documentação interativa em **http://localhost:8080/swagger-ui.html** e teste tudo por lá.

## 🧪 Experimente

**1. Crie um link curto**

```bash
curl -X POST http://localhost:8080/api/urls \
  -H "Content-Type: application/json" \
  -d '{"url": "https://www.example.com/um/endereco/bem/comprido?com=parametros"}'
```

```json
{
  "code": "aB3dE9x",
  "shortUrl": "http://localhost:8080/aB3dE9x",
  "originalUrl": "https://www.example.com/um/endereco/bem/comprido?com=parametros",
  "active": true,
  "managementToken": "q3J0m2v8K1xYw9Zt5bN7cD4eF6gH8iJ0kL2mN4oP6qR"
}
```

> ⚠️ **Guarde o `managementToken`!** Ele aparece só nesta resposta e não dá para recuperá-lo depois.

**2. Acesse o link:** abra `http://localhost:8080/aB3dE9x` no navegador e você cai na página original.

**3. Veja as estatísticas**

```bash
curl http://localhost:8080/api/urls/aB3dE9x/stats \
  -H "Authorization: Bearer SEU_TOKEN"
```

**4. Troque o token quando quiser** (por exemplo, se suspeitar que vazou)

```bash
curl -X POST http://localhost:8080/api/urls/aB3dE9x/rotate-token \
  -H "Authorization: Bearer SEU_TOKEN"
```

O token antigo **morre na hora**, e o novo aparece só nesta resposta.

**5. Desative o link quando quiser**

```bash
curl -X DELETE http://localhost:8080/api/urls/aB3dE9x \
  -H "Authorization: Bearer SEU_TOKEN"
```

## 🗺️ Endpoints

| | Rota | Precisa de token? | O que faz |
|---|---|:---:|---|
| `POST` | `/api/urls` | ❌ | Cria um link curto |
| `GET` | `/{code}` | ❌ | Redireciona para a URL original |
| `GET` | `/api/urls/{code}/stats` | ✅ | Mostra total de cliques e série diária |
| `DELETE` | `/api/urls/{code}` | ✅ | Desativa o link |
| `POST` | `/api/urls/{code}/rotate-token` | ✅ | Troca o token (o antigo deixa de valer) |

<details>
<summary><b>Códigos de resposta</b></summary>

| Código | Quando acontece |
|:---:|---|
| `201` | Link criado |
| `302` | Redirecionamento |
| `204` | Link desativado |
| `400` | URL inválida, data no passado ou período de estatísticas incorreto |
| `401` | Faltou o token |
| `403` | O token não é deste link |
| `429` | Muitas requisições do mesmo IP (veja o header `Retry-After`) |
| `404` | Link inexistente, desativado ou expirado |

Os erros vêm em formato padronizado (RFC 7807), com `title` e `detail` explicando o que houve.

**Estatísticas:** aceitam `?from=2026-11-01&to=2026-11-30`. Sem parâmetros, mostram os últimos 30 dias (máximo de 366). Os dias seguem o horário UTC.

</details>

## ⚙️ Configuração

| Variável | O que é |
|---|---|
| `IP_HASH_SECRET` | Chave usada para anonimizar IPs. **Defina em produção** (gere uma com `openssl rand -hex 32`). Em desenvolvimento já existe um valor padrão. |
| `FORWARD_HEADERS_STRATEGY` | Use `framework` quando a API estiver atrás de proxy ou load balancer, para enxergar o IP real de quem acessa. Sem proxy, deixe o padrão (`none`). |

Os limites de requisições ficam em `app.rate-limit` no `application.yml` (veja abaixo).

## ✅ Testes

```bash
mvn test
```

São **136 testes**: unitários (rápidos) e de integração com um PostgreSQL de verdade, que sobe sozinho via Testcontainers.

> 🐳 Os testes de integração precisam do **Docker** ligado. Sem ele, são pulados e só os unitários rodam.

---

## 💡 Por que foi feito assim?

Cada escolha abaixo tem um motivo. Clique para ler.

<details>
<summary><b>↪️ Por que o redirecionamento é 302 e não 301?</b></summary>

O 301 diz ao navegador "este link mudou **para sempre**", e ele guarda isso na memória. Nas visitas seguintes, nem pergunta ao nosso servidor, vai direto para o destino, e o clique **nunca é contado**.

O 302 ("mudou por enquanto") faz toda visita passar por aqui. Assim a contagem fica correta e desativar ou expirar um link funciona na hora.

**O preço:** mais requisições ao servidor. Para um encurtador que mede cliques, vale a pena.

</details>

<details>
<summary><b>🕵️ Como o IP é protegido?</b></summary>

Nunca guardamos o IP, só uma "impressão digital" dele. Mas uma impressão digital comum (SHA-256) não basta: existem só ~4 bilhões de IPs possíveis, e qualquer pessoa com acesso ao banco conseguiria testar todos e descobrir quem clicou.

Por isso usamos **HMAC com uma chave secreta**: sem a chave, ninguém consegue reverter. E como o resultado é sempre o mesmo para o mesmo IP, ainda dá para contar visitantes distintos.

**Cuidados:**
- Mantenha o `IP_HASH_SECRET` **o mesmo** entre deploys. Se trocar, os hashes novos não batem com os antigos.
- Trate-o como uma senha. Se vazar, a proteção acaba.
- Atrás de proxy ou load balancer, configure `server.forward-headers-strategy: framework`. Senão todos os cliques parecerão vir do mesmo IP.

</details>

<details>
<summary><b>🎲 Por que o código do link é aleatório e não sequencial?</b></summary>

Dava para numerar os links (1, 2, 3...) e converter para letras. Seria simples e sem colisões. Mas aí qualquer pessoa descobriria todos os links existentes só contando, e saberia quantos links o serviço tem.

Com **7 caracteres aleatórios** (letras e números) existem cerca de **3,5 trilhões** de combinações. Adivinhar um código é impraticável.

**O preço:** dois links podem, raramente, sortear o mesmo código. Veja a próxima pergunta.

</details>

<details>
<summary><b>💥 E se dois links sortearem o mesmo código?</b></summary>

O banco de dados é quem decide: ele **recusa** o código repetido, e o serviço sorteia outro e tenta de novo (até 5 vezes). O usuário nem percebe.

A alternativa, "perguntar se o código existe antes de salvar", tem uma falha: duas pessoas podem perguntar ao mesmo tempo, as duas ouvem "está livre", e uma delas toma um erro.

**Detalhe técnico importante:** quando o banco recusa uma inserção, a transação inteira fica inutilizável. Por isso cada tentativa roda numa transação própria (`ShortUrlPersister`), e o `ShortUrlService.create` **não deve** ser anotado com `@Transactional`. Se alguém fizer isso, o retry para de funcionar. Os testes de integração protegem esse comportamento, inclusive com 20 criações simultâneas.

</details>

<details>
<summary><b>🔐 Como o gerenciamento é protegido sem login?</b></summary>

O código do link é público (está no próprio endereço curto), então não serve como senha. Na criação, geramos um **token aleatório de 256 bits**, entregue uma única vez. No banco guardamos só o hash dele, então nem um vazamento do banco expõe os tokens.

- O token vai no header `Authorization`, e não na URL, para não ficar em logs e históricos.
- A comparação leva sempre o mesmo tempo, o que impede descobrir o token "aos poucos".
- A resposta da criação não pode ser guardada em cache.

**Trocar o token:** quem ainda tem o token pode trocá-lo a qualquer momento (`POST /api/urls/{code}/rotate-token`). O antigo deixa de valer imediatamente. Se duas trocas acontecerem ao mesmo tempo com o mesmo token, só uma vence, para ninguém ficar com um token que já nasceu inválido.

**O preço:** a troca exige o token atual, então **ela não recupera um token perdido**: sem ele, não há como provar que você é o dono. Recuperar de verdade exigiria outro canal de prova (como um e-mail cadastrado), que este projeto não tem. Links criados antes desse recurso não têm dono e não aceitam nenhum token.

</details>

<details>
<summary><b>🚦 Como funciona o limite de requisições?</b></summary>

Cada IP tem uma "cota" que se recupera aos poucos, como uma torneira pingando: o padrão é **10 criações de link por minuto** e **30 chamadas de gerenciamento** (`DELETE`, `/stats` e `/rotate-token`) **por minuto**. Passou do limite, a API responde **429** com o header `Retry-After` dizendo quantos segundos esperar.

- **Protege o token:** quem tentar adivinhar tokens é barrado depois de 30 tentativas, e enquanto estiver barrado nem o token certo funciona *para aquele IP*. O dono, de outro IP, não é afetado.
- **O redirecionamento não tem limite:** é o caminho público e quente do serviço, e limitá-lo puniria quem só clica no link.
- **Requisições inválidas também gastam a cota**, senão dava para martelar o servidor com pedidos errados.
- **Os limites são ajustáveis** em `app.rate-limit` (`requests` e `window`), e dá para desligar com `app.rate-limit.enabled=false`.

**Cuidados:**
- O controle fica **na memória** de cada instância. Com várias instâncias, o limite real é multiplicado por elas. Para um limite compartilhado seria preciso algo como o Redis.
- Atrás de proxy, sem `FORWARD_HEADERS_STRATEGY=framework`, **todos os usuários parecem ter o mesmo IP** e dividem uma única cota. Mas só ligue essa opção se houver mesmo um proxy na frente: sem ele, qualquer pessoa consegue forjar o header `X-Forwarded-For` e fugir do limite.

</details>

<details>
<summary><b>📊 Como os cliques são contados?</b></summary>

Cada clique gera dois registros: um **evento** (com referrer e navegador) e um **contador do dia**. O contador usa uma operação única do banco ("insira ou some 1"), então mesmo com muitos cliques ao mesmo tempo nenhum se perde. Isso foi testado com 50 cliques simultâneos.

As estatísticas diárias vêm do contador (rápido), e o total vem dos eventos.

Se a gravação do clique falhar, o usuário **ainda é redirecionado**: estatística não pode derrubar o serviço.

</details>

<details>
<summary><b>🧩 Outras escolhas</b></summary>

- **Só `http` e `https`:** endereços como `javascript:` são rejeitados na criação.
- **URLs de até 2048 caracteres**, guardadas num campo `TEXT` (sem limite de 255).
- **O banco é gerenciado pelo Flyway.** O Hibernate só confere se as entidades batem com o schema (`ddl-auto: validate`).
- **Datas de criação vêm do banco** e ficam nulas na entidade logo após o `save()`. Por isso não aparecem na resposta da criação.
- **Entidades sem `@Data`:** o `equals` e o `toString` gerados dão problema com relações lazy do JPA.

</details>

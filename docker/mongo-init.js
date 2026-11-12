// İlk başlatmada (boş volume) çalışır: uygulama için sadece radar DB'sine yetkili kullanıcı.
const dbName = process.env.MONGO_INITDB_DATABASE;
db.getSiblingDB(dbName).createUser({
  user: process.env.MONGO_APP_USERNAME,
  pwd: process.env.MONGO_APP_PASSWORD,
  roles: [{ role: "readWrite", db: dbName }],
});

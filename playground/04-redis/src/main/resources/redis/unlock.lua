-- Safe release: delete only if the value matches our lock token (prevents deleting someone else's lock).
-- KEYS[1] = lock key, ARGV[1] = token we set at acquire time
if redis.call('GET', KEYS[1]) == ARGV[1] then
  return redis.call('DEL', KEYS[1])
else
  return 0
end

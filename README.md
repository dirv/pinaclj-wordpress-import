# pinaclj-wordpress-import

This tool will help you import files from WordPress into Pinaclj. It imports posts, URLs and terms (tags).

## Usage

`lein run -d <database> -h <host> -u <username> -p <password>`

This will import all posts into a directory named `./pages`. Be careful as it will overwrite any data already there.

## Post-usage

You may want to run some commands like the below in order to fix up any use of WordPress commands.

```
sed -i "" 's/\[\/sourcecode\]/``/g’ *.pina
sed -i "" 's/\[sourcecode[ a-z="]*\]/``/g' *.pina
sed -i "" 's/\[ruby[ a-z="]*\]/``ruby/g' *.pina
sed -i "" 's/\[\/ruby\]/``/g' *.pina
sed -i "" 's/\[\/code\]/``/g' *.pina
sed -i "" 's/\[code\]/``/g' *.pina

sed -i "" 's/&quot;/'/g' *.pina
sed -i "" 's/&gt;/>/g' *.pina
sed -i "" 's/&lt;/</g' *.pina
sed -i "" 's/&lsquo;/''/g' *.pina
sed -i "" 's/&rsquo;/''/g' *.pina>
```

